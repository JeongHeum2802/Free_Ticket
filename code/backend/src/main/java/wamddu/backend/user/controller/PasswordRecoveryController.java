package wamddu.backend.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.user.domain.EmailVerification.Purpose;
import wamddu.backend.user.dto.response.MessageResponse;
import wamddu.backend.user.service.EmailVerificationService;
import wamddu.backend.user.service.PasswordRecoveryService;

@RestController
@RequestMapping("/api/auth/password-resets")
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final EmailVerificationService verification;
    private final PasswordRecoveryService recovery;

    public record SendRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record VerifyRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(min = 43, max = 43) String verificationToken,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
    public record ResetRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(min = 43, max = 43) String verificationToken,
                               @Size(max = 72) String newPassword, @Size(max = 72) String confirmPassword) {}
    public record SendResponse(String verificationToken, int expiresIn, String message) {}

    @PostMapping("/email")
    public ResponseEntity<SendResponse> send(@Valid @RequestBody SendRequest request) {
        // Send the same challenge regardless of membership; disclose account eligibility only after ownership proof.
        String token = verification.send(request.email(), Purpose.PASSWORD_RESET);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SendResponse(token, 300,
                "인증번호를 보냈습니다. 5분 이내에 인증과 비밀번호 변경을 완료해 주세요."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<MessageResponse> verify(@Valid @RequestBody VerifyRequest request) {
        verification.verify(request.email(), request.verificationToken(), request.code(), Purpose.PASSWORD_RESET);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MessageResponse.from("이메일 인증이 완료되었습니다."));
    }

    @PostMapping
    public ResponseEntity<MessageResponse> reset(@Valid @RequestBody ResetRequest request) {
        recovery.reset(request.email(), request.verificationToken(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MessageResponse.from("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요."));
    }
}
