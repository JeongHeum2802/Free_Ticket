package wamddu.backend.user.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "username", "email", "phonenumber", "role", "eventDirector"})
public class UserInfoResponse {
    private Long id;
    private String username;
    private String email;
    private String phonenumber;
    private Role role;
    private boolean eventDirector;

    public static UserInfoResponse from(User user, boolean eventDirector) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phonenumber(user.getPhonenumber())
                .role(user.getRole())
                .eventDirector(eventDirector)
                .build();
    }
}
