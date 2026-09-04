package wamddu.backend.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ErrorResponse {

    private final String code;
    private final String message;

    public static ErrorResponse error(String code, String message) {
        return new ErrorResponse(code, message);
    }
}
