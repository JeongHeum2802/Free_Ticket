package wamddu.backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import wamddu.backend.global.response.ErrorResponse;
import wamddu.backend.user.dto.request.SignupRequest;
import wamddu.backend.user.validation.PasswordPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every error leaves the API as {@code {code, message}}. Spring MVC's own exceptions (bad JSON,
 * unsupported method, unknown path, ...) are handled by the parent class and reshaped here.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "접근 권한이 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        // The cause stays in the server log; the client never sees exception details.
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.getBindingResult().getTarget() instanceof SignupRequest) {
            Map<String, String> errors = new LinkedHashMap<>();
            for (String field : List.of("username", "password", "email", "phonenumber", "emailVerificationToken")) {
                var fieldError = exception.getBindingResult().getFieldError(field);
                if (fieldError != null) {
                    errors.put(field, fieldError.getDefaultMessage());
                }
            }
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("VALIDATION_ERROR", "입력값을 확인해 주세요.", errors));
        }
        // Same code and message as the password reset API, so clients can show the rule.
        for (var fieldError : exception.getBindingResult().getFieldErrors()) {
            if ("ValidPassword".equals(fieldError.getCode())) {
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("INVALID_PASSWORD", PasswordPolicy.MESSAGE));
            }
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_REQUEST", "요청 값을 확인해 주세요."));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed with {}", status.value(), exception);
        }
        return ResponseEntity.status(status).headers(headers).body(errorFor(status));
    }

    private static ErrorResponse errorFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorResponse.of("INVALID_REQUEST", "요청 값을 확인해 주세요.");
            case 404 -> ErrorResponse.of("NOT_FOUND", "요청한 주소를 찾을 수 없습니다.");
            case 405 -> ErrorResponse.of("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
            case 406 -> ErrorResponse.of("NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다.");
            case 415 -> ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다.");
            default -> status.is5xxServerError()
                    ? ErrorResponse.of("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                    : ErrorResponse.of("INVALID_REQUEST", "요청을 처리할 수 없습니다.");
        };
    }
}
