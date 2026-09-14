package wamddu.backend.user.service;

import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.UserStatus;
import wamddu.backend.user.dto.request.*;
import wamddu.backend.user.dto.response.*;
import wamddu.backend.user.repository.UserRepository;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
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
    public SignupResponse signUp(SignupRequest request) {
        log.debug("[SQL CHECK] POST /api/auth/signup START");
        try {
            // 이메일 중복 검사
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
            }

            // 전화번호 중복 검사
            if (userRepository.existsByPhonenumber(request.getPhonenumber())) {
                throw new ApiException(HttpStatus.CONFLICT, "PHONENUMBER_ALREADY_EXISTS", "이미 등록된 전화번호입니다.");
            }

            User user = new User();
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setEmail(request.getEmail());
            user.setPhonenumber(request.getPhonenumber());
            user.setRole(Role.USER);
            user.setStatus(UserStatus.ACTIVE);
            user.setCustomerKey(generateCustomerKey());
            User savedUser = userRepository.save(user);

            return SignupResponse.of("회원가입이 완료되었습니다.", UserInfoResponse.from(savedUser));
        } finally {
            log.debug("[SQL CHECK] POST /api/auth/signup END");
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        log.debug("[SQL CHECK] POST /api/auth/login START");
        try {
            User user = userRepository.findByEmail(request.getEmail());

            if (user == null || user.getStatus() == UserStatus.DELETED || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
            }

            String accessToken = jwtProvider.generateJwtToken(user.getId(), user.getRole().name());
            String refreshToken = jwtProvider.generateRefreshToken(user.getId());

            LoginResponse response = LoginResponse.builder()
                    .message("로그인에 성공했습니다.")
                    .accessToken(accessToken)
                    .tokenType("Bearer")
                    .expiresIn(1800)
                    .user(UserInfoResponse.from(user))
                    .build();

            return new LoginResult(response, refreshToken);
        } finally {
            log.debug("[SQL CHECK] POST /api/auth/login END");
        }
    }

    @Transactional
    public TokenReissueResult reissueToken(String refreshToken) {
        log.debug("[SQL CHECK] POST /api/auth/refresh START");
        try {
            if (refreshToken == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "로그인 정보가 없습니다.");
            }

            try {
                if (!jwtProvider.validateToken(refreshToken)) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.");
                }
            } catch (ExpiredJwtException exception) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.");
            }

            Long id = jwtProvider.getId(refreshToken);
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

            if (user.getStatus() == UserStatus.DELETED) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
            }

            String newToken = jwtProvider.generateJwtToken(user.getId(), user.getRole().name());
            String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());

            RefreshResponse response = RefreshResponse.builder()
                    .accessToken(newToken)
                    .tokenType("Bearer")
                    .expiresIn(1800)
                    .build();

            return new TokenReissueResult(response, newRefreshToken);
        } finally {
            log.debug("[SQL CHECK] POST /api/auth/refresh END");
        }
    }

    public MyInfoResponse getMyInfo(UserDetails userDetails) {
        log.debug("[SQL CHECK] GET /api/auth/me START");
        try {
            User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

            if (user.getStatus() == UserStatus.DELETED) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
            }

            return MyInfoResponse.from(UserInfoResponse.from(user));
        } finally {
            log.debug("[SQL CHECK] GET /api/auth/me END");
        }
    }

    @Transactional
    public UpdateMyInfoResponse updateMyInfo(UpdateMyInfoRequest request, UserDetails userDetails) {
        log.debug("[SQL CHECK] POST /api/users/me START");
        try {
            User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

            if (user.getStatus() == UserStatus.DELETED) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
            }

            if (request.getEmail() != null) {
                if (userRepository.existsByEmail(request.getEmail())) {
                    throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
                }
                user.setEmail(request.getEmail());
            }

            if (request.getPhonenumber() != null) {
                if (userRepository.existsByPhonenumber(request.getPhonenumber())) {
                    throw new ApiException(HttpStatus.CONFLICT, "PHONENUMBER_ALREADY_EXISTS", "이미 등록된 전화번호입니다.");
                }
                user.setPhonenumber(request.getPhonenumber());
            }

            if (request.getUsername() != null) {
                user.setUsername(request.getUsername());
            }

            User updatedUser = userRepository.save(user);

            return UpdateMyInfoResponse.of("회원 정보가 수정되었습니다.", UserInfoResponse.from(updatedUser));
        } finally {
            log.debug("[SQL CHECK] POST /api/users/me END");
        }
    }

    @Transactional
    public MessageResponse updateMyPassword(ChangePasswordRequest request, UserDetails userDetails) {
        log.debug("[SQL CHECK] PATCH /api/users/me/password START");
        try {
            User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

            if (user.getStatus() == UserStatus.DELETED) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
            }

            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다.");
            }

            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_AS_CURRENT_PASSWORD", "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
            }

            String newPassword = passwordEncoder.encode(request.getNewPassword());
            user.setPassword(newPassword);
            userRepository.save(user);

            return MessageResponse.from("비밀번호가 변경되었습니다.");
        } finally {
            log.debug("[SQL CHECK] PATCH /api/users/me/password END");
        }
    }

    @Transactional
    public MessageResponse deleteMyAccount(DeleteAccountRequest request, UserDetails userDetails) {
        log.debug("[SQL CHECK] DELETE /api/users/me START");
        try {
            User user = userRepository.findById(Long.parseLong(userDetails.getUsername()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

            if (user.getStatus() == UserStatus.DELETED) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호가 올바르지 않습니다.");
            }

            user.setStatus(UserStatus.DELETED);
            user.setEmail("deleted_" + System.currentTimeMillis() + "_" + user.getEmail());
            if (user.getPhonenumber() != null) {
                user.setPhonenumber("deleted_" + System.currentTimeMillis() + "_" + user.getPhonenumber());
            }
            userRepository.save(user);
            return MessageResponse.from("회원 탈퇴가 완료되었습니다.");
        } finally {
            log.debug("[SQL CHECK] DELETE /api/users/me END");
        }
    }
}
