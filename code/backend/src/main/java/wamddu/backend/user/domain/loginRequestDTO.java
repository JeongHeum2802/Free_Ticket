package wamddu.backend.user.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class loginRequestDTO {
    private String email;
    private String password;
}
