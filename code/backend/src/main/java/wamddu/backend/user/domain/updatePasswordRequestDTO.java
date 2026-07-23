package wamddu.backend.user.domain;

import lombok.Getter;

@Getter
public class updatePasswordRequestDTO {
    private String oldPassword;
    private String newPassword;
}
