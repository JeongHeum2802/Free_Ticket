import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useEffect, useState } from "react";
import { getSellingTickets, getTicketPriceHistory, type SellingTicket, type PriceHistory } from "../../api/sellingTickets";
import TicketPriceChart from "../../components/TicketPriceChart";
import TicketPriceSettings from "../../components/TicketPriceSettings";
import { formatHistoryDate } from "../../lib/formatHistoryDate";

export default function SellingTickets() {
  const { user, loading } = useAuth();

  if (loading) {
    return null;
  }

  if (user?.eventDirector !== true) {
    return <Navigate to="/mypage/tickets" replace />;
  }

  return <SellingTicketList key={user.id} />;
}

function ticketStatus(ticket: SellingTicket) {
  if (ticket.bookingEndtime && new Date(ticket.bookingEndtime).getTime() <= Date.now()) return "판매 종료";
  if (ticket.totalTicket !== null && ticket.soldTicket !== null && ticket.soldTicket >= ticket.totalTicket) return "매진";
  return ticket.bookingEndtime ? "판매 중" : "일정 미정";
}

function SellingTicketList() {
  const [tickets, setTickets] = useState<SellingTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState<SellingTicket | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getSellingTickets(controller.signal).then((data) => {
      if (!controller.signal.aborted) setTickets(data);
    }).catch(() => {
      if (!controller.signal.aborted) setError(true);
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [attempt]);

  return <section>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold">내가 판매 중인 티켓</h1>
        <p className="mt-2 text-sm text-gray-500">담당 행사의 티켓을 선택해 가격 변동을 확인하세요. 종료된 판매도 포함됩니다.</p>
      </div>
      {!loading && !error && <span className="rounded-full bg-indigo-50 px-3 py-1 text-sm font-semibold text-indigo-700">총 {tickets.length}종</span>}
    </div>
    {loading ? <p role="status" className="py-16 text-center text-gray-500">판매 티켓을 불러오는 중입니다.</p>
      : error ? <div role="alert" className="mt-6 rounded-xl bg-red-50 p-6 text-center">
        <p>판매 티켓을 불러오지 못했습니다.</p>
        <button className="mt-3 rounded-lg border px-4 py-2" onClick={() => { setLoading(true); setError(false); setAttempt(attempt + 1); }}>다시 시도</button>
      </div>
      : tickets.length === 0 ? <p className="mt-6 rounded-xl border border-gray-200 py-16 text-center text-gray-500">담당 행사에 등록된 티켓이 없습니다.</p>
      : <div className="mt-6 grid gap-3 sm:grid-cols-2">
        {tickets.map((ticket) => <button key={ticket.id} type="button" aria-pressed={selected?.id === ticket.id}
          onClick={() => setSelected(ticket)} aria-controls="ticket-price-history"
          className={`rounded-xl border p-5 text-left transition hover:border-indigo-400 focus-visible:outline-2 focus-visible:outline-indigo-600 ${selected?.id === ticket.id ? "border-indigo-500 bg-indigo-50 ring-1 ring-indigo-500" : "border-gray-200 bg-white"}`}>
          <span className="flex items-center justify-between gap-2 text-xs text-gray-500"><span>{ticket.eventName}</span><span className="shrink-0 rounded-full bg-gray-100 px-2 py-1">{ticketStatus(ticket)}</span></span>
          <span className="mt-3 block font-semibold">{ticket.type}</span>
          <span className="mt-2 block text-xs text-gray-500">{ticket.startTime ? formatHistoryDate(ticket.startTime) : "공연 일정 미정"}</span>
          <span className="mt-4 flex flex-wrap items-end justify-between gap-2"><strong className="text-xl">{ticket.price === null ? "가격 미정" : `${ticket.price.toLocaleString()}원`}</strong><span className="text-xs text-gray-500">판매 {ticket.soldTicket ?? "-"} / {ticket.totalTicket ?? "-"}매</span></span>
          <span className="mt-3 block text-xs font-semibold text-indigo-700">가격 이력 보기 →</span>
        </button>)}
      </div>}
    {!loading && !error && tickets.length > 0 && <div id="ticket-price-history" className="mt-8">
      {selected ? <PriceHistoryPanel key={selected.id} ticket={selected} />
        : <p className="rounded-xl border border-dashed border-gray-300 bg-gray-50 px-5 py-12 text-center text-sm text-gray-500">위에서 티켓을 선택하면 가격 이력 차트가 표시됩니다.</p>}
    </div>}
  </section>;
}

function PriceHistoryPanel({ ticket }: { ticket: SellingTicket }) {
  const [data, setData] = useState<PriceHistory | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getTicketPriceHistory(ticket.id, controller.signal).then((result) => {
      if (!controller.signal.aborted) setData(result);
    }).catch(() => {
      if (!controller.signal.aborted) setError(true);
    });
    return () => controller.abort();
  }, [ticket.id, attempt]);

  const prices = data?.history.map((point) => point.price) ?? [];
  const summary = [
    ["현재 판매가", data?.ticket.price],
    ["이력 최저가", prices.length ? Math.min(...prices) : null],
    ["이력 최고가", prices.length ? Math.max(...prices) : null],
  ] as const;

  return <section aria-label="선택한 티켓 가격 이력" className="rounded-2xl border border-gray-200 p-5 sm:p-6">
    <h2 className="text-lg font-bold">가격 변동 이력</h2>
    <p className="mt-1 text-sm text-gray-500">{ticket.type}</p>
    {error ? <div role="alert" className="py-10 text-center text-red-600">
      <p>가격 이력을 불러오지 못했습니다.</p>
      <button className="mt-3 rounded-lg border px-4 py-2" onClick={() => { setError(false); setAttempt(attempt + 1); }}>다시 시도</button>
    </div> : !data ? <p role="status" className="py-12 text-center text-gray-500">가격 이력을 불러오는 중입니다.</p>
      : <>
        <dl className="my-6 grid gap-3 sm:grid-cols-3">{summary.map(([label, value]) => <div key={label} className="rounded-xl bg-gray-50 p-4"><dt className="text-xs text-gray-500">{label}</dt><dd className="mt-2 text-lg font-bold">{value == null ? "기록 없음" : `${value.toLocaleString()}원`}</dd></div>)}</dl>
        <TicketPriceChart history={data.history} />
        <TicketPriceSettings currentPrice={data.ticket.price} />
        {data.history.length > 0 && <details className="mt-5 rounded-lg border border-gray-200 p-4">
          <summary className="cursor-pointer text-sm font-semibold">전체 변경 내역 ({data.history.length}건)</summary>
          <div className="mt-4 max-h-72 overflow-auto"><table className="w-full text-left text-sm">
            <caption className="sr-only">{ticket.type} 가격 변경 내역</caption>
            <thead><tr className="border-b text-gray-500"><th scope="col" className="py-2">변경 일시</th><th scope="col" className="py-2 text-right">가격</th></tr></thead>
            <tbody>{data.history.map((point) => <tr key={point.id} className="border-b border-gray-100"><td className="py-3">{formatHistoryDate(point.changedAt)}</td><td className="py-3 text-right font-medium">{point.price.toLocaleString()}원</td></tr>)}</tbody>
          </table></div>
        </details>}
      </>}
  </section>;
}
