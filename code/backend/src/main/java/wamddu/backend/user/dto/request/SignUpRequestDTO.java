package wamddu.backend.user.dto.request;

import lombok.Getter;

@Getter
public class SignUpRequestDTO {
    private String username;
    private String password;
    private String email;
    private String phonenumber;
}