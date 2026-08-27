import { api } from "./axios";
import type { ApiDataResponse, PaymentResult } from "../types/Payment";

type ConfirmPaymentRequest = {
  paymentKey: string;
  orderId: string;
  amount: number;
};

export async function confirmPayment(request: ConfirmPaymentRequest) {
  const response = await api.post<ApiDataResponse<PaymentResult>>(
    "/payments/confirm",
    request,
  );
  return response.data.data;
}
