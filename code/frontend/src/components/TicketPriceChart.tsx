import { useState } from "react";
import type { PricePoint } from "../api/sellingTickets";
import { formatHistoryDate } from "../lib/formatHistoryDate";

export default function TicketPriceChart({ history }: { history: PricePoint[] }) {
  const [activeId, setActiveId] = useState<number | null>(null);
  if (!history.length) {
    return <p className="rounded-xl bg-gray-50 px-6 py-16 text-center text-gray-500">아직 기록된 가격 이력이 없습니다.</p>;
  }

  const prices = history.map((point) => point.price);
  const minimum = Math.min(...prices);
  const maximum = Math.max(...prices);
  const padding = Math.max((maximum - minimum) * 0.2, maximum * 0.05, 1000);
  const lower = Math.max(0, minimum - padding);
  const upper = maximum + padding;
  const times = history.map((point) => new Date(point.changedAt).getTime());
  const first = times[0];
  const span = times[times.length - 1] - first;
  const points = history.map((point, index) => ({
    ...point,
    x: span === 0 ? 395 : 90 + ((times[index] - first) / span) * 610,
    y: 235 - ((point.price - lower) / (upper - lower)) * 190,
  }));
  const active = points.find((point) => point.id === activeId) ?? points[points.length - 1];
  const path = points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");

  return (
    <div>
      <div aria-live="polite" className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-indigo-50 px-4 py-3 text-sm">
        <span className="text-gray-600">{formatHistoryDate(active.changedAt)}</span>
        <strong className="text-indigo-700">{active.price.toLocaleString()}원</strong>
      </div>
      <div className="overflow-x-auto rounded-xl border border-gray-100 bg-gray-50/50 p-2">
        <svg viewBox="0 0 740 290" className="min-w-[520px] w-full" role="img" aria-label="티켓 가격 변동 차트. 가로축은 변경 일시, 세로축은 원 단위 가격입니다.">
          <title>티켓 가격 이력</title>
          <desc>각 지점에 마우스를 올리거나 키보드로 이동하면 가격을 확인할 수 있습니다. 아래 표에서도 전체 이력을 확인할 수 있습니다.</desc>
          {[0, 1, 2, 3, 4].map((tick) => {
            const y = 45 + tick * 47.5;
            const price = upper - (tick / 4) * (upper - lower);
            return <g key={tick}>
              <line x1="90" y1={y} x2="700" y2={y} stroke="#e5e7eb" strokeDasharray="4 4" />
              <text x="78" y={y + 4} textAnchor="end" fontSize="12" fill="#6b7280">{Math.round(price).toLocaleString()}</text>
            </g>;
          })}
          <text x="90" y="24" fontSize="12" fill="#6b7280">가격 (원)</text>
          <path d={path} fill="none" stroke="#4f46e5" strokeWidth="3" strokeLinejoin="round" />
          {points.map((point) => (
            <circle key={point.id} cx={point.x} cy={point.y} r={active.id === point.id ? 7 : 5}
              fill={active.id === point.id ? "#4f46e5" : "white"} stroke="#4f46e5" strokeWidth="2"
              tabIndex={0} role="button" className="cursor-pointer focus:outline-2 focus:outline-indigo-700"
              aria-label={`${formatHistoryDate(point.changedAt)}, ${point.price.toLocaleString()}원`}
              onMouseEnter={() => setActiveId(point.id)} onFocus={() => setActiveId(point.id)}
              onClick={() => setActiveId(point.id)}
              onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setActiveId(point.id); } }}>
              <title>{formatHistoryDate(point.changedAt)} · {point.price.toLocaleString()}원</title>
            </circle>
          ))}
          <text x="90" y="268" fontSize="12" fill="#6b7280">{history[0].changedAt.slice(0, 10)}</text>
          {history.length > 1 && <text x="700" y="268" textAnchor="end" fontSize="12" fill="#6b7280">{history[history.length - 1].changedAt.slice(0, 10)}</text>}
        </svg>
      </div>
    </div>
  );
}
