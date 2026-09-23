package wamddu.backend.order.domain;

public enum OrderStatus {
    PENDING,
    CONFIRMING,
    CANCELING,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    EXPIRED
}
