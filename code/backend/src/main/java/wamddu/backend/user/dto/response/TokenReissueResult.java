package wamddu.backend.user.dto.response;

public record TokenReissueResult(RefreshResponse response, String refreshToken) {
}
