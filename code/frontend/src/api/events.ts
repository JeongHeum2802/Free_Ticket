import { api } from "./axios";
import type {
  EventCategory,
  EventDetailResponse,
  EventListResponse,
  RankedEvent,
  RankedEventsResponse,
} from "../types/Event";

type EventQuery = {
  category?: EventCategory;
  limit?: number;
};

export async function getEvents(category?: EventCategory) {
  const response = await api.get<EventListResponse>("/events", {
    params: category ? { category } : undefined,
  });

  return response.data.data.events;
}

export async function getWhatsHot({ category, limit }: EventQuery = {}) {
  const response = await api.get<RankedEventsResponse>("/events/whats-hot", {
    params: { category, limit },
  });

  return response.data.data.events;
}

export async function getWeeklyRanking({ category, limit }: EventQuery = {}) {
  const response = await api.get<RankedEventsResponse>(
    "/events/weekly-ranking",
    { params: { category, limit } },
  );

  return response.data.data.events;
}

export async function getEventDetail(eventId: number) {
  const response = await api.get<EventDetailResponse>(`/events/${eventId}`);

  return response.data.data;
}

export function hasBannerImage(
  event: RankedEvent,
): event is RankedEvent & { bannerImageUrl: string } {
  return Boolean(event.bannerImageUrl?.trim() && event.mainImageUrl.trim());
}
