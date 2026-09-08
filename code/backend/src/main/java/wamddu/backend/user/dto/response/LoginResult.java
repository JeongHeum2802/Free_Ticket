package wamddu.backend.user.dto.response;

public record LoginResult(LoginResponse response, String refreshToken) {
}
