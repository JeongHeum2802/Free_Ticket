import { useId, useState } from "react";

export default function TicketPriceSettings({ currentPrice }: { currentPrice: number | null }) {
  const id = useId();
  const [automatic, setAutomatic] = useState(false);
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [manualPrice, setManualPrice] = useState(currentPrice?.toString() ?? "");
  const invalidRange = automatic && minPrice !== "" && maxPrice !== "" && Number(minPrice) > Number(maxPrice);
  const inputClass = "mt-2 w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 pr-10 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400";

  return (
    <section aria-label="티켓 가격 설정" className="mt-5 rounded-xl border border-gray-200 bg-gray-50 p-4 sm:p-5">
      <h3 className="font-bold text-gray-900">가격 설정</h3>
      <label className="mt-4 flex cursor-pointer items-center gap-2 text-sm font-semibold">
        <input type="checkbox" checked={automatic} onChange={(event) => setAutomatic(event.target.checked)}
          className="h-4 w-4 accent-indigo-600" />
        자동 가격 알고리즘 사용
      </label>

      <fieldset disabled={!automatic} className="mt-4 grid gap-4 sm:grid-cols-2">
        <legend className="sr-only">자동 가격 범위</legend>
        <div>
          <label htmlFor={`${id}-min`} className="text-sm font-medium text-gray-700">최소 가격 (MIN)</label>
          <div className="relative">
            <input id={`${id}-min`} type="number" min="0" step="1" inputMode="numeric"
              value={minPrice} onChange={(event) => setMinPrice(event.target.value)} placeholder="최소 가격 입력"
              aria-invalid={invalidRange} aria-describedby={invalidRange ? `${id}-range-error` : undefined} className={inputClass} />
            <span aria-hidden="true" className="absolute right-3 top-5 text-sm text-gray-400">원</span>
          </div>
        </div>
        <div>
          <label htmlFor={`${id}-max`} className="text-sm font-medium text-gray-700">최대 가격 (MAX)</label>
          <div className="relative">
            <input id={`${id}-max`} type="number" min="0" step="1" inputMode="numeric"
              value={maxPrice} onChange={(event) => setMaxPrice(event.target.value)} placeholder="최대 가격 입력"
              aria-invalid={invalidRange} aria-describedby={invalidRange ? `${id}-range-error` : undefined} className={inputClass} />
            <span aria-hidden="true" className="absolute right-3 top-5 text-sm text-gray-400">원</span>
          </div>
        </div>
      </fieldset>
      {invalidRange && <p id={`${id}-range-error`} role="alert" className="mt-2 text-xs text-red-600">최대 가격은 최소 가격 이상이어야 합니다.</p>}

      <div className="mt-5 border-t border-gray-200 pt-4">
        <label htmlFor={`${id}-manual`} className="text-sm font-medium text-gray-700">가격 수정</label>
        <div className="relative">
          <input id={`${id}-manual`} type="number" min="0" step="1" inputMode="numeric" disabled={automatic}
            value={manualPrice} onChange={(event) => setManualPrice(event.target.value)} placeholder="수정할 가격 입력"
            aria-describedby={`${id}-manual-help`} className={inputClass} />
          <span aria-hidden="true" className="absolute right-3 top-5 text-sm text-gray-400">원</span>
        </div>
        <p id={`${id}-manual-help`} className="mt-2 text-xs text-gray-500">
          {automatic ? "자동 가격 알고리즘을 해제하면 직접 가격을 입력할 수 있습니다." : "자동 가격 알고리즘을 사용하지 않을 때 직접 가격을 입력할 수 있습니다."}
        </p>
      </div>
      <p className="mt-4 text-xs text-gray-500">입력한 설정은 아직 저장되지 않으며, 실제 판매 가격에 반영되지 않습니다.</p>
    </section>
  );
}
