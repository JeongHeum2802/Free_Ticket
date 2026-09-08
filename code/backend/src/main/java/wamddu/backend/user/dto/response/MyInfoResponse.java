package wamddu.backend.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MyInfoResponse {
    private UserInfoResponse user;

    public static MyInfoResponse from(UserInfoResponse user) {
        return new MyInfoResponse(user);
    }
}
