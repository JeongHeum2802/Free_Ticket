package wamddu.backend.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyInfoRequest {
    private String username;
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(min = 3, max = 254)
    private String email;
    private String phonenumber;
    @jakarta.validation.constraints.Size(max = 43)
    private String emailVerificationToken;
}
