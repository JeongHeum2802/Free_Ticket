import { useEffect, useState } from "react";
import { getPublicTicketPriceHistory, type PublicPriceHistory } from "../api/ticketPriceHistory";
import TicketPriceChart from "./TicketPriceChart";
import { formatHistoryDate } from "../lib/formatHistoryDate";

type Props = { eventId: number; ticketId: number; ticketType: string };

export default function BuyerTicketPriceHistory(props: Props) {
  return <HistoryContent key={`${props.eventId}-${props.ticketId}`} {...props} />;
}

function HistoryContent({ eventId, ticketId, ticketType }: Props) {
  const [data, setData] = useState<PublicPriceHistory | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getPublicTicketPriceHistory(eventId, ticketId, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setData(result); })
      .catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [eventId, ticketId, attempt]);

  const prices = data?.history.map((point) => point.price) ?? [];
  const summaries = [
    ["현재 판매가", data?.currentPrice],
    ["기록된 최저가", prices.length ? Math.min(...prices) : null],
    ["기록된 최고가", prices.length ? Math.max(...prices) : null],
  ] as const;

  return <section aria-label="구매자용 티켓 가격 이력" className="mt-8 min-w-0 rounded-2xl border border-indigo-100 bg-white p-4 sm:p-5">
    <h2 className="text-lg font-black text-gray-900">티켓 가격 히스토리</h2>
    <p className="mt-1 text-sm text-gray-500">{ticketType}</p>
    <p className="mt-2 text-xs leading-5 text-gray-500">티켓 1매 기준의 가격 변동입니다. 구매 수량과 관계없이 표시됩니다.</p>
    {error ? <div role="alert" className="py-8 text-center text-sm">
      <p className="text-gray-600">가격 이력을 불러오지 못했습니다. 예매는 계속 진행할 수 있습니다.</p>
      <button type="button" className="mt-3 rounded-lg border border-gray-300 px-4 py-2 font-semibold" onClick={() => { setError(false); setAttempt(attempt + 1); }}>가격 이력 다시 시도</button>
    </div> : !data ? <p role="status" className="py-10 text-center text-sm text-gray-500">가격 이력을 불러오는 중입니다.</p>
      : <>
        <dl className="my-5 grid grid-cols-1 gap-2 sm:grid-cols-3">
          {summaries.map(([label, price]) => <div key={label} className="rounded-xl bg-[#f5f4ff] p-3">
            <dt className="text-xs text-gray-500">{label}</dt>
            <dd className="mt-2 font-bold text-gray-900">{price == null ? "기록 없음" : `${price.toLocaleString()}원`}</dd>
          </div>)}
        </dl>
        <TicketPriceChart history={data.history} />
        {data.history.length > 0 && <details className="mt-4 text-sm">
          <summary className="cursor-pointer font-semibold text-gray-700">가격 변경 내역 {data.history.length}건</summary>
          <div className="mt-3 max-h-60 overflow-auto">
            <table className="w-full text-left text-xs">
              <caption className="sr-only">선택한 티켓의 가격 변경 내역</caption>
              <thead><tr className="border-b text-gray-500"><th scope="col" className="py-2">변경 일시</th><th scope="col" className="py-2 text-right">1매 가격</th></tr></thead>
              <tbody>{data.history.map((point) => <tr key={point.id} className="border-b border-gray-100"><td className="py-3">{formatHistoryDate(point.changedAt)}</td><td className="py-3 text-right font-semibold">{point.price.toLocaleString()}원</td></tr>)}</tbody>
            </table>
          </div>
        </details>}
      </>}
  </section>;
}
