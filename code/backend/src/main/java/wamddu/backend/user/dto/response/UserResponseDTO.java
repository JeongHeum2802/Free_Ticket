package wamddu.backend.user.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;
import wamddu.backend.user.domain.Role;

@Getter
@Setter
@JsonPropertyOrder({"id", "username", "email", "phonenumber", "role"})
public class UserResponseDTO {
    private Long id;
    private String username;
    private String email;
    private String phonenumber;
    private Role role;
}
