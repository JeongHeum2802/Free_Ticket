import { api } from "./axios";
import type { ApiDataResponse } from "../types/Payment";
import type { PricePoint } from "./sellingTickets";

export type PublicPriceHistory = {
  ticketId: number;
  currentPrice: number | null;
  history: PricePoint[];
};

export async function getPublicTicketPriceHistory(eventId: number, ticketId: number, signal?: AbortSignal) {
  const response = await api.get<ApiDataResponse<PublicPriceHistory>>(
    `/events/${eventId}/tickets/${ticketId}/price-history`, { signal },
  );
  return response.data.data;
}
