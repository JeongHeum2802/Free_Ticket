import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { searchTicketChat } from '../api/ticketChat';
import type { EventSummary } from '../types/Event';
import './TicketChat.css';

type Message = { role: 'assistant' | 'user'; text: string; events?: EventSummary[] };
const greeting: Message = { role: 'assistant', text: '어떤 공연을 찾고 있나요?\n지역, 날짜, 장르, 예산을 자유롭게 알려주세요.' };
const suggestions = ['서울에서 볼 뮤지컬 찾아줘', '이번 주말 공연 찾아줘', '5만 원 이하 티켓 찾아줘'];

function ChatIcon() {
  return (
    <svg aria-hidden="true" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
      <path d="M21 11.5a9 9 0 0 1-9 8.5 10 10 0 0 1-3.5-.65L3 21l1.5-5A8.5 8.5 0 0 1 3 11.5a9 9 0 0 1 18 0Z" strokeLinejoin="round" />
      <path d="M8 11.5h.01m4 0h.01m4 0h.01" strokeWidth="2.8" strokeLinecap="round" />
    </svg>
  );
}

export default function TicketChat() {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<Message[]>([greeting]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const launcher = useRef<HTMLButtonElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const conversation = useRef<HTMLDivElement>(null);
  const composing = useRef(false);

  useEffect(() => {
    if (open) input.current?.focus();
  }, [open]);

  useEffect(() => {
    const element = conversation.current;
    if (element) element.scrollTop = messages.length === 1 ? 0 : element.scrollHeight;
  }, [messages, open, pending, error]);

  function close() {
    setOpen(false);
    launcher.current?.focus();
  }

  async function send(text: string) {
    const trimmed = text.trim();
    if (!trimmed || trimmed.length > 1000 || inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    setError('');
    const previous = messages;
    const next: Message[] = [...messages, { role: 'user', text: trimmed }];
    setMessages(next);
    setDraft('');
    try {
      const reply = await searchTicketChat(next.filter(message => message.role === 'user').map(message => message.text).slice(-6));
      setMessages([...next, { role: 'assistant', text: reply.message, events: reply.events }]);
    } catch (cause) {
      setMessages(previous);
      setDraft(trimmed);
      const serverMessage = axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message : undefined;
      setError(serverMessage || 'AI 검색에 연결하지 못했습니다. 잠시 후 다시 보내 주세요.');
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  function reset() {
    if (inFlight.current) return;
    setMessages([greeting]);
    setDraft('');
    setError('');
    input.current?.focus();
  }

  return (
    <div className="ticket-chat">
      {open && (
        <section id="ticket-chat-panel" className="ticket-chat-panel" role="dialog" aria-labelledby="ticket-chat-title"
          onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); close(); } }}>
          <header className="ticket-chat-header">
            <div className="ticket-chat-heading">
              <h2 id="ticket-chat-title">Free Ticket</h2>
              <p>나에게 맞는 공연 찾기</p>
            </div>
            <button type="button" className="ticket-chat-reset" onClick={reset} disabled={pending}>새 대화</button>
            <button type="button" className="ticket-chat-close" onClick={close} aria-label="대화창 닫기">
              <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="m6 6 12 12M18 6 6 18" /></svg>
            </button>
          </header>
          <div className="ticket-chat-notice">최근 질문 6개를 Google Gemini로 전송합니다. 개인정보는 입력하지 마세요.</div>
          <div className="ticket-chat-conversation" ref={conversation}>
            <div role="log" aria-label="대화 내용" aria-live="polite" aria-relevant="additions">
              {messages.map((message, index) => (
                <div key={index} className={`ticket-chat-message ticket-chat-message-${message.role}`}>
                  <span className="ticket-chat-speaker">{message.role === 'assistant' ? '프리티켓' : '나'}</span>
                  <p>{message.text}</p>
                  {message.events && message.events.length > 0 && (
                    <ul className="ticket-chat-results" aria-label="공연 검색 결과">
                      {message.events.map(event => (
                        <li key={event.id}>
                          <Link to={`/ticket/${event.id}`} onClick={close}>
                            <strong>{event.name}</strong>
                            <span>{event.location}</span>
                            <span>{event.startDate} ~ {event.endDate}</span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
              {pending && <p role="status">공연을 찾고 있어요…</p>}
            </div>
            {error && <p className="ticket-chat-error" role="alert">{error}</p>}
            {messages.length === 1 && (
              <div className="ticket-chat-suggestions" aria-label="추천 질문">
                <span>이렇게 시작해 보세요</span>
                {suggestions.map(text => <button key={text} type="button" onClick={() => send(text)}>{text}</button>)}
              </div>
            )}
          </div>
          <form className="ticket-chat-form" onSubmit={event => { event.preventDefault(); send(draft); }}>
            <div className="ticket-chat-input-wrap">
              <textarea ref={input} aria-label="공연 검색 메시지" placeholder="어떤 공연을 찾고 있나요?" rows={2} maxLength={1000}
                readOnly={pending} aria-busy={pending}
                value={draft} onChange={event => setDraft(event.target.value)}
                onCompositionStart={() => { composing.current = true; }}
                onCompositionEnd={() => { composing.current = false; }}
                onKeyDown={event => {
                  if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && !composing.current && event.keyCode !== 229) {
                    event.preventDefault(); send(draft);
                  }
                }} />
              <button type="submit" className="ticket-chat-send" aria-label="메시지 보내기" disabled={pending || !draft.trim()}>
                <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 19V5m-6 6 6-6 6 6" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </button>
            </div>
            <p>Enter로 전송 · Shift + Enter로 줄바꿈<br />검색 조건을 확인해 주세요. 가격·잔여 수량은 상세 화면에서 확인할 수 있어요.</p>
          </form>
        </section>
      )}
      <button ref={launcher} type="button" className="ticket-chat-launcher" aria-label={open ? '채팅 닫기' : '채팅 열기'}
        aria-expanded={open} aria-controls={open ? 'ticket-chat-panel' : undefined} onClick={() => open ? close() : setOpen(true)}>
        <ChatIcon />
      </button>
    </div>
  );
}
