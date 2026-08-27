export type CheckoutOrder = {
  orderId: string;
  orderName: string;
  amount: number;
  quantity: number;
  customerKey: string;
  customerName: string;
  customerEmail: string;
  expiresAt: string;
};

export type PaymentResult = {
  orderId: string;
  paymentKey: string;
  amount: number;
  method: string;
  status: string;
  approvedAt: string;
  receiptUrl?: string;
};

export type ReservationHistory = {
  orderId: string;
  eventId: number;
  eventName: string;
  mainImageUrl: string;
  location: string;
  ticketType: string;
  performanceAt: string | null;
  quantity: number;
  amount: number;
  paidAt: string;
  paymentMethod: string | null;
  receiptUrl: string | null;
  status: "PAID";
};

export type ApiDataResponse<T> = {
  message: string;
  data: T;
};
