package wamddu.backend.order.domain;

public enum OrderStatus {
    PENDING,
    CONFIRMING,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    EXPIRED
}
