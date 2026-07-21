package wamddu.backend.user.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"id", "username", "email", "phonenumber", "role"})
public class userResponseDTO {
    private Long id;
    private String username;
    private String email;
    private String phonenumber;
    private Role role;
}
