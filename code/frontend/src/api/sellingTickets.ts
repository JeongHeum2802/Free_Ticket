import { api } from "./axios";
import type { ApiDataResponse } from "../types/Payment";

export type SellingTicket = {
  id: number;
  eventId: number;
  eventName: string;
  type: string;
  price: number | null;
  totalTicket: number | null;
  soldTicket: number | null;
  startTime: string | null;
  bookingEndtime: string | null;
};

export type PricePoint = { id: number; price: number; changedAt: string };
export type PriceHistory = { ticket: SellingTicket; history: PricePoint[] };

export async function getSellingTickets(signal?: AbortSignal) {
  const response = await api.get<ApiDataResponse<SellingTicket[]>>("/tickets/me/selling", { signal });
  return response.data.data;
}

export async function getTicketPriceHistory(ticketId: number, signal?: AbortSignal) {
  const response = await api.get<ApiDataResponse<PriceHistory>>(`/tickets/${ticketId}/price-history`, { signal });
  return response.data.data;
}
