import { useEffect, useRef, useState } from 'react';
import './TicketChat.css';

type Message = { role: 'assistant' | 'user'; text: string };
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
  const [messages, setMessages] = useState<Message[]>([
    { role: 'assistant', text: '어떤 공연을 찾고 있나요?\n지역, 날짜, 장르, 예산을 자유롭게 알려주세요.' },
  ]);
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
  }, [messages, open]);

  function close() {
    setOpen(false);
    launcher.current?.focus();
  }

  function send(text: string) {
    const trimmed = text.trim();
    if (!trimmed) return;
    setMessages(previous => [...previous,
      { role: 'user', text: trimmed },
      { role: 'assistant', text: '조건을 입력해 주셨어요! 지금은 대화 화면을 미리 체험하는 단계예요. 실제 공연·티켓 검색은 AI 연결 후 제공될 예정이에요.' },
    ]);
    setDraft('');
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
            <button type="button" className="ticket-chat-close" onClick={close} aria-label="대화창 닫기">
              <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="m6 6 12 12M18 6 6 18" /></svg>
            </button>
          </header>
          <div className="ticket-chat-preview">미리보기 화면이에요. 실제 검색은 준비 중입니다.</div>
          <div className="ticket-chat-conversation" ref={conversation}>
            <div role="log" aria-label="대화 내용" aria-live="polite" aria-relevant="additions">
              {messages.map((message, index) => (
                <div key={index} className={`ticket-chat-message ticket-chat-message-${message.role}`}>
                  <span className="ticket-chat-speaker">{message.role === 'assistant' ? '프리티켓' : '나'}</span>
                  <p>{message.text}</p>
                </div>
              ))}
            </div>
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
                value={draft} onChange={event => setDraft(event.target.value)}
                onCompositionStart={() => { composing.current = true; }}
                onCompositionEnd={() => { composing.current = false; }}
                onKeyDown={event => {
                  if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && !composing.current && event.keyCode !== 229) {
                    event.preventDefault(); send(draft);
                  }
                }} />
              <button type="submit" className="ticket-chat-send" aria-label="메시지 보내기" disabled={!draft.trim()}>
                <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 19V5m-6 6 6-6 6 6" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </button>
            </div>
            <p>Enter로 전송 · Shift + Enter로 줄바꿈</p>
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
