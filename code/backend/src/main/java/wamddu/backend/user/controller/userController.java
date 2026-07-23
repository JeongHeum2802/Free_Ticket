package wamddu.backend.user.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.user.domain.*;
import wamddu.backend.user.service.userService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class userController {

    private final userService userService;

    @PostMapping("/api/auth/signup")
    public ResponseEntity<Map<String,Object>> signUp(@RequestBody signUpRequestDTO requestDTO) {
        return  userService.signUp(requestDTO);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String,Object>> login(@RequestBody loginRequestDTO requestDTO) {
        return userService.login(requestDTO);
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<Map<String,Object>> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        return userService.reissueToken(refreshToken);
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<Map<String, Object>> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getMyInfo(userDetails);
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        result.put("message", "로그아웃되었습니다.");

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping("/api/users/me")
    public ResponseEntity<Map<String, Object>> updateMyInfo(
            @RequestBody updateRequestDTO requestDTO,
            @AuthenticationPrincipal UserDetails userDetails) {
        return userService.updateMyInfo(requestDTO, userDetails);
    }

    @PatchMapping("/api/users/me/password")
    public ResponseEntity<Map<String, Object>> updateMyPassword(
            @RequestBody updatePasswordRequestDTO requestDTO,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return userService.updateMyPassword(requestDTO, userDetails);
    }

    @DeleteMapping("/api/users/me")
    public ResponseEntity<Map<String, Object>> deleteMyAccount(
            @RequestBody deleteUserRequestDTO requestDTO,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletResponse response) {
        return userService.deleteMyAccount(requestDTO, userDetails, response);
    }
}
