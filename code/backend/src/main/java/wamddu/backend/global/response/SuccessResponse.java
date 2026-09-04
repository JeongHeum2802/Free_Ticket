package wamddu.backend.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SuccessResponse<T> {

    private final String message;
    private final T data;

    public static <T> SuccessResponse<T> success(String message, T data) {
        return new SuccessResponse<>(message, data);
    }
}
