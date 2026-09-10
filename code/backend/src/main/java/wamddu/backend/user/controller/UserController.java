package wamddu.backend.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.user.dto.request.*;
import wamddu.backend.user.dto.response.*;
import wamddu.backend.user.service.UserService;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/auth/signup")
    public ResponseEntity<SignupResponse> signUp(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult result = userService.login(request);
        ResponseCookie cookie = createRefreshTokenCookie(result.refreshToken(), 604800);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<RefreshResponse> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        TokenReissueResult result = userService.reissueToken(refreshToken);
        ResponseCookie cookie = createRefreshTokenCookie(result.refreshToken(), 604800);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<MyInfoResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getMyInfo(userDetails));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<MessageResponse> logout() {
        ResponseCookie cookie = createExpiredRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(MessageResponse.from("로그아웃되었습니다."));
    }

    @PostMapping("/api/users/me")
    public ResponseEntity<UpdateMyInfoResponse> updateMyInfo(
            @RequestBody UpdateMyInfoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.updateMyInfo(request, userDetails));
    }

    @PatchMapping("/api/users/me/password")
    public ResponseEntity<MessageResponse> updateMyPassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.updateMyPassword(request, userDetails));
    }

    @DeleteMapping("/api/users/me")
    public ResponseEntity<MessageResponse> deleteMyAccount(
            @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        MessageResponse response = userService.deleteMyAccount(request, userDetails);
        ResponseCookie cookie = createExpiredRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAge) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/refresh")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/refresh")
                .maxAge(0)
                .build();
    }
}
