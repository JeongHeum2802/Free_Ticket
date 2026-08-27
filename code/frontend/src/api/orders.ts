import { api } from "./axios";
import type {
  ApiDataResponse,
  CheckoutOrder,
  ReservationHistory,
} from "../types/Payment";

export async function createOrder(ticketId: number, quantity: number) {
  const response = await api.post<ApiDataResponse<CheckoutOrder>>("/orders", {
    ticketId,
    quantity,
  });
  return response.data.data;
}

export async function getCheckoutOrder(orderId: string) {
  const response = await api.get<ApiDataResponse<CheckoutOrder>>(
    `/orders/${encodeURIComponent(orderId)}/checkout`,
  );
  return response.data.data;
}

export async function getMyReservations() {
  const response = await api.get<
    ApiDataResponse<{ reservations: ReservationHistory[] }>
  >("/orders/me/reservations");
  return response.data.data.reservations;
}
