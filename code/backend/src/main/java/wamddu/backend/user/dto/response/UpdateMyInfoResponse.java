package wamddu.backend.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyInfoResponse {
    private String message;
    private UserInfoResponse user;

    public static UpdateMyInfoResponse of(String message, UserInfoResponse user) {
        return new UpdateMyInfoResponse(message, user);
    }
}
