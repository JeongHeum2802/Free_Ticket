import { api } from "./axios";
import type {
  EventCategory,
  EventDetail,
  EventDetailResponse,
  EventListResponse,
  HotEvent,
  RankedEventsResponse,
  WeeklyRankedEvent,
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
  const response = await api.get<RankedEventsResponse<HotEvent>>(
    "/events/whats-hot",
    { params: { category, limit } },
  );

  return response.data.data.events;
}

export async function getWeeklyRanking({ category, limit }: EventQuery = {}) {
  const response = await api.get<RankedEventsResponse<WeeklyRankedEvent>>(
    "/events/weekly-ranking",
    { params: { category, limit } },
  );

  return response.data.data.events;
}

export async function getEventDetail(eventId: number) {
  const response = await api.get<EventDetailResponse>(`/events/${eventId}`);
  const { event, ticketOptions } = response.data.data;
  const { running_time: legacyRunningTime, runningTime, ...eventFields } = event;
  const normalizedEvent: EventDetail = {
    ...eventFields,
    runningTime: runningTime ?? legacyRunningTime ?? 0,
  };

  return {
    event: normalizedEvent,
    ticketOptions,
  };
}
