package wamddu.backend.user.domain;

import lombok.Getter;

@Getter
public class UpdateRequestDTO {
    private String username;
    private String email;
    private String phonenumber;
}
