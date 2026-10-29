// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, test, vi } from 'vitest';
import { api } from '../api/axios';
import TicketChat from './TicketChat';

afterEach(() => { cleanup(); vi.restoreAllMocks(); });
const response = { data: { data: { message: '서울 뮤지컬 검색 결과입니다.', events: [
  { id: 401, name: '테스트 뮤지컬', location: '서울', startDate: '2026-10-31', endDate: '2026-11-01' },
] } } };
function open() {
  render(<MemoryRouter><TicketChat /></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', { name: '채팅 열기' }));
}
function send(text: string) {
  fireEvent.change(screen.getByLabelText('공연 검색 메시지'), { target: { value: text } });
  fireEvent.click(screen.getByRole('button', { name: '메시지 보내기' }));
}

test('검색 결과 링크를 표시하고 후속 질문에 사용자 대화를 전달한다', async () => {
  const post = vi.spyOn(api, 'post').mockResolvedValue(response);
  open();
  send('서울 뮤지컬');
  expect((await screen.findByRole('link', { name: /테스트 뮤지컬/ })).getAttribute('href')).toBe('/ticket/401');
  send('그럼 부산은?');
  await waitFor(() => expect(post).toHaveBeenLastCalledWith('/events/chat',
    { messages: ['서울 뮤지컬', '그럼 부산은?'] }, { timeout: 40000 }));
});

test('응답 대기 중 중복 요청을 막고 실패한 입력을 복원해 재시도한다', async () => {
  let reject!: (reason: Error) => void;
  const post = vi.spyOn(api, 'post').mockImplementationOnce(() => new Promise((_, fail) => { reject = fail; }))
    .mockResolvedValue(response);
  open();
  send('서울 뮤지컬');
  expect((screen.getByRole('button', { name: '메시지 보내기' }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.keyDown(screen.getByLabelText('공연 검색 메시지'), { key: 'Enter' });
  expect(post).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error('offline')));
  expect(await screen.findByRole('alert')).toBeTruthy();
  expect((screen.getByLabelText('공연 검색 메시지') as HTMLTextAreaElement).value).toBe('서울 뮤지컬');
  fireEvent.click(screen.getByRole('button', { name: '메시지 보내기' }));
  await screen.findByRole('link', { name: /테스트 뮤지컬/ });
  expect(post).toHaveBeenLastCalledWith('/events/chat', { messages: ['서울 뮤지컬'] }, { timeout: 40000 });
  expect(screen.queryByRole('alert')).toBeNull();
});

test('새 대화는 이전 검색 조건을 지운다', async () => {
  const post = vi.spyOn(api, 'post').mockResolvedValue(response);
  open();
  send('서울 뮤지컬');
  await screen.findByRole('link', { name: /테스트 뮤지컬/ });
  fireEvent.click(screen.getByRole('button', { name: '새 대화' }));
  send('콘서트');
  await waitFor(() => expect(post).toHaveBeenLastCalledWith('/events/chat',
    { messages: ['콘서트'] }, { timeout: 40000 }));
});
