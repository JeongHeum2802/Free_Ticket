package wamddu.backend.user.service;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.user.domain.EmailVerification.Purpose;
import wamddu.backend.user.domain.UserStatus;
import wamddu.backend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final EmailVerificationService verification;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RefreshTokenStore refreshTokens;

    @Transactional
    public void reset(String email, String token, String password, String confirmation) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                        || Character.isISOControl(c) || c == 0xFEFF)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 공백 없이 8자 이상, 영문·숫자 기준 최대 72자로 입력해 주세요. 한글 등은 더 짧을 수 있습니다.");
        }
        if (!password.equals(confirmation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호 확인이 일치하지 않습니다.");
        }
        email = EmailVerificationService.normalizeEmail(email);
        // All account writes lock user before verification, preventing stale profile writes and lock inversion.
        var user = users.findByEmail(email);
        // Verification consumption and password update commit together; failures leave the proof usable.
        verification.consume(email, token, Purpose.PASSWORD_RESET);
        if (user == null || user.getStatus() != UserStatus.ACTIVE
                || !EmailVerificationService.normalizeEmail(user.getEmail()).equals(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_UNAVAILABLE", "이 이메일로 비밀번호를 변경할 수 없습니다. 가입한 이메일인지 확인해 주세요.");
        }
        if (encoder.matches(password, user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_AS_CURRENT_PASSWORD", "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        user.setPassword(encoder.encode(password));
        refreshTokens.revokeAll(user.getId());
    }
}
