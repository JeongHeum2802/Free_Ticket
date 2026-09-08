package wamddu.backend.user.domain;

import lombok.Getter;

@Getter
public class UpdatePasswordRequestDTO {
    private String currentPassword;
    private String newPassword;
}
