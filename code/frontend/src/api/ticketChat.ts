import { api } from './axios';
import type { EventSummary } from '../types/Event';

export type ChatReply = { message: string; events: EventSummary[] };

export async function searchTicketChat(messages: string[]): Promise<ChatReply> {
  const response = await api.post<{ data: ChatReply }>('/events/chat', { messages }, { timeout: 40_000 });
  return response.data.data;
}
