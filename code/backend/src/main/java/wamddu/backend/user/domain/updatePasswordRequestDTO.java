package wamddu.backend.user.domain;

import lombok.Getter;

@Getter
public class updatePasswordRequestDTO {
    private String currentPassword;
    private String newPassword;
}
