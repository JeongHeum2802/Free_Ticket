package wamddu.backend.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //400 BAD REQUEST
    TICKET_NOT_FOUND(HttpStatus.BAD_REQUEST, "TICKET_NOT_FOUND", "존재하지 않는 티켓입니다."),
    QUANTITY_ERROR(HttpStatus.BAD_REQUEST, "QUANTITY_ERROR", "티켓은 최소 1장 최대 10장까지 구매 가능합니다."),
    ORDER_NOT_PENDING(HttpStatus.BAD_REQUEST, "ORDER_NOT_PENDING", "처리된 주문입니다."),
    ORDER_NOT_FOUND(HttpStatus.BAD_REQUEST, "ORDER_NOT_FOUND", "잘못된 OrderId입니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "수량이 다릅니다."),
    INVALID_EVENT_CATEGORY(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다."),
    INVALID_LIMIT(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit은 1 이상 20 이하로 입력해 주세요."),
    INVALID_EVENT_ID(HttpStatus.BAD_REQUEST, "INVALID_EVENT_ID", "올바른 이벤트 ID를 입력해 주세요."),

    //401 UNAUTHORIZED
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요한 서비스입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않는 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),

    //404 NOT FOUND
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "이벤트를 찾을 수 없습니다."),

    //409 CONFLICT
    TICKET_NOT_REMAINING(HttpStatus.CONFLICT, "TICKET_NOT_REMAINING", "티켓 수량이 부족합니다."),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "ORDER_EXPIRED", "만료된 주문입니다."),

    //500 INTERNAL SERVER ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    //502 TOSS_CONFIRM_FAILED
    TOSS_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "TOSS_CONFIRM_FAILED", "토스 결제 승인이 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
