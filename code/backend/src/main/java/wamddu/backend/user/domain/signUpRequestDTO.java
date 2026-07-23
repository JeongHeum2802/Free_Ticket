package wamddu.backend.user.domain;

import lombok.Getter;

@Getter
public class signUpRequestDTO {
    private String username;
    private String password;
    private String email;
    private String phonenumber;
}