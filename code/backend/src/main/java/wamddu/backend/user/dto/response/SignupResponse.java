package wamddu.backend.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {
    private String message;
    private UserInfoResponse user;

    public static SignupResponse of(String message, UserInfoResponse user) {
        return new SignupResponse(message, user);
    }
}
