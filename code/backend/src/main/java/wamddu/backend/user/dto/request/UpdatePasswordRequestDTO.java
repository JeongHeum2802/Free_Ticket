package wamddu.backend.user.dto.request;

import lombok.Getter;

@Getter
public class UpdatePasswordRequestDTO {
    private String currentPassword;
    private String newPassword;
}
