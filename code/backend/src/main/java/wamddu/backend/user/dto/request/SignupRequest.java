package wamddu.backend.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @NotNull(message = "사용자 이름은 2자 이상이어야 합니다.")
    @Size(min = 2, message = "사용자 이름은 2자 이상이어야 합니다.")
    private String username;
    @NotNull(message = "비밀번호는 8자 이상이어야 합니다.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;
    @NotNull(message = "올바른 이메일 형식이 아닙니다.")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    @NotNull(message = "올바른 전화번호 형식이 아닙니다.")
    @Size(min = 11, max = 11, message = "올바른 전화번호 형식이 아닙니다.")
    private String phonenumber;
}
