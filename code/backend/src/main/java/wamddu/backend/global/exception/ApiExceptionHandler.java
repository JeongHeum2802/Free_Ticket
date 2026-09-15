package wamddu.backend.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import wamddu.backend.global.response.ErrorResponse;
import wamddu.backend.user.dto.request.SignupRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        if (exception.getBindingResult().getTarget() instanceof SignupRequest) {
            Map<String, String> errors = new LinkedHashMap<>();
            for (String field : List.of("username", "password", "email", "phonenumber")) {
                var fieldError = exception.getBindingResult().getFieldError(field);
                if (fieldError != null) {
                    errors.put(field, fieldError.getDefaultMessage());
                }
            }
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("VALIDATION_ERROR", "입력값을 확인해 주세요.", errors));
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_REQUEST", "요청 값을 확인해 주세요."));
    }
}
