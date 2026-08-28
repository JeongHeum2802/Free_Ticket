package wamddu.backend.user.service;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.user.domain.*;
import wamddu.backend.user.repository.userRepository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class userService {

    private final PasswordEncoder passwordEncoder;
    private final userRepository userRepository;
    private final JwtProvider jwtProvider;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static String generateCustomerKey() {
        byte[] randomBytes = new byte[16];
        SECURE_RANDOM.nextBytes(randomBytes);

        String randomString = URL_ENCODER.encodeToString(randomBytes);

        return "customer_" + randomString;
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> signUp(signUpRequestDTO signUpRequestDTO) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, String> error = new LinkedHashMap<>();

        //이메일 중복 검사
        if(userRepository.existsByEmail(signUpRequestDTO.getEmail())) {
            response.put("code", "EMAIL_ALREADY_EXISTS");
            response.put("message", "이미 사용 중인 이메일입니다.");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

//        //사용자 이름 중복 검사
//        if(userRepository.existsByUsername(signUpRequestDTO.getUsername())) {
//            response.put("code", "USERNAME_ALREADY_EXISTS");
//            response.put("message", "이미 사용 중인 사용자 이름입니다.");
//
//            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
//        }

        //전화번호 중복 검사
        if(userRepository.existsByPhonenumber(signUpRequestDTO.getPhonenumber())) {
            response.put("code", "PHONENUMBER_ALREADY_EXISTS");
            response.put("message", "이미 등록된 전화번호입니다.");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        //입력값 검사
        String regex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
        if(signUpRequestDTO.getUsername().length() < 2
        || signUpRequestDTO.getPassword().length() < 8
        || !signUpRequestDTO.getEmail().matches(regex)
        || signUpRequestDTO.getPhonenumber().length() != 11) {
            response.put("code", "VALIDATION_ERROR");
            response.put("message", "입력값을 확인해 주세요.");
            error.put("username", "사용자 이름은 2자 이상이어야 합니다.");
            error.put("password", "비밀번호는 8자 이상이어야 합니다.");
            error.put("email", "올바른 이메일 형식이 아닙니다.");
            error.put("phonenumber", "올바른 전화번호 형식이 아닙니다.");

            response.put("errors", error);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            User user = new User();

            user.setUsername(signUpRequestDTO.getUsername());
            String hashed = passwordEncoder.encode(signUpRequestDTO.getPassword());
            user.setPassword(hashed);
            user.setEmail(signUpRequestDTO.getEmail());
            user.setPhonenumber(signUpRequestDTO.getPhonenumber());
            user.setRole(Role.USER);
            user.setCustomerKey(generateCustomerKey());
            User savedUser = userRepository.save(user);

            userResponseDTO responseUser = new userResponseDTO();
            responseUser.setId(savedUser.getId());
            responseUser.setUsername(savedUser.getUsername());
            responseUser.setEmail(savedUser.getEmail());
            responseUser.setPhonenumber(savedUser.getPhonenumber());
            responseUser.setRole(savedUser.getRole());

            response.put("message", "회원가입이 완료되었습니다.");
            response.put("user", responseUser);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch(Exception ex){
            response.put("code", "INTERNAL_SERVER_ERROR");
            response.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> login(loginRequestDTO loginRequestDTO) {
        Map<String, Object> response = new LinkedHashMap<>();

        User user = userRepository.findByEmail(loginRequestDTO.getEmail());

        if(user == null || !passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())){
            response.put("code", "INVALID_CREDENTIALS");
            response.put("message", "이메일 또는 비밀번호가 올바르지 않습니다.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = jwtProvider.generateJwtToken(user.getId(), user.getRole().name());

        userResponseDTO responseUser = new userResponseDTO();
        responseUser.setId(user.getId());
        responseUser.setUsername(user.getUsername());
        responseUser.setEmail(user.getEmail());
        responseUser.setPhonenumber(user.getPhonenumber());
        responseUser.setRole(user.getRole());

        response.put("message", "로그인에 성공했습니다.");
        response.put("accessToken", token);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 1800);
        response.put("user", responseUser);

        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/refresh")
                .maxAge(604800)
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> reissueToken(String refreshToken) {

        Map<String, Object> response = new LinkedHashMap<>();
        if(refreshToken == null) {
            response.put("code", "REFRESH_TOKEN_NOT_FOUND");
            response.put("message", "로그인 정보가 없습니다.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            if(!jwtProvider.validateToken(refreshToken)) {
                response.put("code", "INVALID_REFRESH_TOKEN");
                response.put("message", "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.");

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (ExpiredJwtException exception) {
            response.put("code", "REFRESH_TOKEN_EXPIRED");
            response.put("message", "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Long id = jwtProvider.getId(refreshToken);
        User user =  userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("서버 오류"));

        String newToken = jwtProvider.generateJwtToken(user.getId(), user.getRole().name());
        response.put("accessToken", newToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 1800);

        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());
        ResponseCookie cookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/refresh")
                .maxAge(604800)
                .build();

        return  ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> getMyInfo(UserDetails userDetails) {
        Map<String, Object> response = new LinkedHashMap<>();
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                .orElseThrow(() -> new IllegalArgumentException("서버 오류"));

        userResponseDTO responseUser = new userResponseDTO();
        responseUser.setId(user.getId());
        responseUser.setUsername(user.getUsername());
        responseUser.setEmail(user.getEmail());
        responseUser.setPhonenumber(user.getPhonenumber());
        responseUser.setRole(user.getRole());

        response.put("user" , responseUser);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> updateMyInfo(updateRequestDTO updateRequestDTO, UserDetails userDetails) {
        Map<String, Object> response = new LinkedHashMap<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                .orElseThrow(() -> new IllegalArgumentException("서버 오륲"));

        if(updateRequestDTO.getEmail() != null){
            if(userRepository.existsByEmail(updateRequestDTO.getEmail())){
                response.put("code", "EMAIL_ALREADY_EXISTS");
                response.put("message", "이미 사용 중인 이메일입니다.");

                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            user.setEmail(updateRequestDTO.getEmail());
        }

        if(updateRequestDTO.getPhonenumber() != null){
            if(userRepository.existsByPhonenumber(updateRequestDTO.getPhonenumber())){
                response.put("code", "PHONENUMBER_ALREADY_EXISTS");
                response.put("message", "이미 등록된 전화번호입니다.");

                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            user.setPhonenumber(updateRequestDTO.getPhonenumber());
        }

        if(updateRequestDTO.getUsername() != null){
//            if(userRepository.existsByUsername(updateRequestDTO.getUsername())){
//                response.put("code", "USERNAME_ALREADY_EXISTS");
//                response.put("message", "이미 사용 중인 사용자 이름입니다.");
//
//                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
//            }

            user.setUsername(updateRequestDTO.getUsername());
        }

        userRepository.save(user);

        userResponseDTO responseUser = new userResponseDTO();
        responseUser.setId(user.getId());
        responseUser.setUsername(user.getUsername());
        responseUser.setEmail(user.getEmail());
        responseUser.setPhonenumber(user.getPhonenumber());
        responseUser.setRole(user.getRole());

        response.put("message" , "회원 정보가 수정되었습니다.");
        response.put("user" , responseUser);
        return  ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> updateMyPassword(updatePasswordRequestDTO requestDTO, UserDetails userDetails) {
        Map<String, Object> response = new LinkedHashMap<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                .orElseThrow(() -> new IllegalArgumentException("서버 오류"));

        if(!passwordEncoder.matches(requestDTO.getCurrentPassword(), user.getPassword())){
            response.put("code", "INVALID_CURRENT_PASSWORD");
            response.put("message", "현재 비밀번호가 올바르지 않습니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if(passwordEncoder.matches(requestDTO.getNewPassword(), user.getPassword())){
            response.put("code", "SAME_AS_CURRENT_PASSWORD");
            response.put("message", "새 비밀번호는 현재 비밀번호와 달라야 합니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String newPassword = passwordEncoder.encode(requestDTO.getNewPassword());
        user.setPassword(newPassword);
        userRepository.save(user);

        response.put("message", "비밀번호가 변경되었습니다.");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> deleteMyAccount(deleteUserRequestDTO deleteUserRequestDTO,
                                                               UserDetails userDetails,
                                                               HttpServletResponse httpServletResponse) {
        Map<String, Object> response = new LinkedHashMap<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                .orElseThrow(() -> new IllegalArgumentException("서버 오류"));
        if(!passwordEncoder.matches(deleteUserRequestDTO.getPassword(), user.getPassword())){
            response.put("code", "INVALID_PASSWORD");
            response.put("message", "비밀번호가 올바르지 않습니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        userRepository.delete(user);
        response.put("message", "회원 탈퇴가 완료되었습니다.");

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
