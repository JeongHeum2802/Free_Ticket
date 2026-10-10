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
import wamddu.backend.user.dto.response.MessageResponse;
import wamddu.backend.user.service.EmailVerificationService;

@RestController
@RequestMapping("/api/auth/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService service;

    public record SendRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record VerifyRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(min = 43, max = 43) String verificationToken,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
    public record SendResponse(String verificationToken, int expiresIn, String message) {}

    @PostMapping
    public ResponseEntity<SendResponse> send(@Valid @RequestBody SendRequest request) {
        String token = service.send(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SendResponse(token, 300, "인증번호를 보냈습니다. 5분 이내에 인증과 가입·저장을 완료해 주세요."));
    }

    @PostMapping("/verify")
    public MessageResponse verify(@Valid @RequestBody VerifyRequest request) {
        service.verify(request.email(), request.verificationToken(), request.code());
        return MessageResponse.from("이메일 인증이 완료되었습니다.");
    }
}
