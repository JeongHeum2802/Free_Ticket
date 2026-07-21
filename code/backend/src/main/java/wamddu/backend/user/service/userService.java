package wamddu.backend.user.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.user.domain.*;
import wamddu.backend.user.repository.userRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class userService {

    private final PasswordEncoder passwordEncoder;
    private final userRepository userRepository;
    private final JwtProvider jwtProvider;

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

        //사용자 이름 중복 검사
        if(userRepository.existsByUsername(signUpRequestDTO.getUsername())) {
            response.put("code", "USERNAME_ALREADY_EXISTS");
            response.put("message", "이미 사용 중인 사용자 이름입니다.");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

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

        if(user == null || passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())){
            response.put("code", "INVALID_CREDENTIALS");
            response.put("message", "이메일 또는 비밀번호가 올바르지 않습니다.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String token = jwtProvider.generateJwtToken(user.getEmail(), user.getRole().name());

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

        String refreshToken = jwtProvider.generateRefreshToken(user.getEmail());
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(604800)
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }
}
