type TossAmount = { currency: "KRW"; value: number };

type TossPaymentRequest = {
  orderId: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
  customerEmail?: string;
  customerName?: string;
};

type TossPaymentWidgets = {
  setAmount(amount: TossAmount): Promise<void>;
  renderPaymentMethods(options: { selector: string; variantKey?: string }): Promise<unknown>;
  renderAgreement(options: { selector: string; variantKey?: string }): Promise<unknown>;
  requestPayment(options: TossPaymentRequest): Promise<void>;
};

type TossPaymentsInstance = {
  widgets(options: { customerKey: string }): TossPaymentWidgets;
};

interface Window {
  TossPayments?: (clientKey: string) => TossPaymentsInstance;
}
