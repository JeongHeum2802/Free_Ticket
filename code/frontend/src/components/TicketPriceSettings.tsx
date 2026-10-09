import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { updateTicketPriceSettings, type SellingTicket } from "../api/sellingTickets";

function validPrice(value: string) {
  const price = Number(value);
  return value !== "" && Number.isInteger(price) && price > 0 && price <= 2147483647;
}

export default function TicketPriceSettings({ ticket, onSaved }: { ticket: SellingTicket; onSaved: (ticket: SellingTicket) => void }) {
  const id = useId();
  const [savedTicket, setSavedTicket] = useState(ticket);
  const [automatic, setAutomatic] = useState(ticket.automaticPricingEnabled);
  const [minPrice, setMinPrice] = useState(ticket.minPrice?.toString() ?? "");
  const [initialPrice, setInitialPrice] = useState(ticket.initialPrice?.toString() ?? "");
  const [manualPrice, setManualPrice] = useState(ticket.price?.toString() ?? "");
  const [manualPriceEdited, setManualPriceEdited] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const inputClass = "mt-2 w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 pr-10 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400 read-only:bg-gray-100";

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaved(false);
    setError(null);
    if (automatic && (!validPrice(initialPrice) || !validPrice(minPrice))) {
      setError("초기 가격과 최소 가격은 1원 이상 2,147,483,647원 이하의 정수로 입력해 주세요.");
      return;
    }
    if (automatic && Number(minPrice) > Number(initialPrice)) {
      setError("최소 가격은 초기 가격 이하여야 합니다.");
      return;
    }
    if (automatic && savedTicket.initialPrice !== null && (savedTicket.price === null || Number(minPrice) > savedTicket.price || savedTicket.price > Number(initialPrice))) {
      setError("자동 가격을 사용하려면 현재 판매가가 최소 가격과 초기 가격 사이여야 합니다.");
      return;
    }
    if (!automatic && !validPrice(manualPrice)) {
      setError("수동 가격은 1원 이상 2,147,483,647원 이하의 정수로 입력해 주세요.");
      return;
    }

    const controller = new AbortController();
    request.current = controller;
    setSaving(true);
    try {
      const updated = await updateTicketPriceSettings(ticket.id, {
        initialPrice: automatic ? savedTicket.initialPrice ?? Number(initialPrice) : savedTicket.initialPrice,
        minPrice: automatic ? Number(minPrice) : savedTicket.minPrice,
        automaticPricingEnabled: automatic,
        price: !automatic && manualPriceEdited ? Number(manualPrice) : null,
      }, controller.signal);
      if (controller.signal.aborted) return;
      setSavedTicket(updated);
      setAutomatic(updated.automaticPricingEnabled);
      setInitialPrice(updated.initialPrice?.toString() ?? "");
      setMinPrice(updated.minPrice?.toString() ?? "");
      setManualPrice(updated.price?.toString() ?? "");
      setManualPriceEdited(false);
      setSaved(true);
      onSaved(updated);
    } catch {
      if (!controller.signal.aborted) setError("가격 설정을 저장하지 못했습니다. 다시 시도해 주세요.");
    } finally {
      if (!controller.signal.aborted) setSaving(false);
    }
  }

  return (
    <section aria-label="티켓 가격 설정" className="mt-5 rounded-xl border border-gray-200 bg-gray-50 p-4 sm:p-5">
      <h3 className="font-bold text-gray-900">가격 설정</h3>
      <form onSubmit={save} noValidate aria-busy={saving} onChange={() => { setError(null); setSaved(false); }}>
        <label className="mt-4 flex cursor-pointer items-center gap-2 text-sm font-semibold">
          <input type="checkbox" checked={automatic} disabled={saving} onChange={(event) => setAutomatic(event.target.checked)}
            className="h-4 w-4 accent-indigo-600" />
          자동 가격 알고리즘 사용
        </label>
        <p className="mt-2 text-xs text-gray-500">자동 가격은 2시간마다 최근 2시간의 결제 완료 수량을 반영해 조정되며, 최소 가격과 초기 가격 사이로 유지됩니다. 자동 조정은 최초 활성화 후 2시간부터 시작합니다. 마감까지 2시간 이하라면 수동 가격을 설정해 주세요.</p>

        <fieldset disabled={!automatic || saving} className="mt-4 grid gap-4 sm:grid-cols-2">
          <legend className="sr-only">자동 가격 범위</legend>
          <div>
            <label htmlFor={`${id}-initial`} className="text-sm font-medium text-gray-700">초기 가격 (P0)</label>
            <div className="relative">
              <input id={`${id}-initial`} type="number" min="1" max="2147483647" step="1" inputMode="numeric"
                value={initialPrice} onChange={(event) => setInitialPrice(event.target.value)} readOnly={savedTicket.initialPrice !== null}
                placeholder="초기 가격 입력" aria-describedby={`${id}-initial-help`} className={inputClass} />
              <span aria-hidden="true" className="absolute right-3 top-5 text-sm text-gray-400">원</span>
            </div>
            <p id={`${id}-initial-help`} className="mt-2 text-xs text-gray-500">최초 자동 가격 설정 시 적용되며, 저장 후 변경할 수 없습니다.</p>
          </div>
          <div>
            <label htmlFor={`${id}-min`} className="text-sm font-medium text-gray-700">최소 가격 (MIN)</label>
            <div className="relative">
              <input id={`${id}-min`} type="number" min="1" max="2147483647" step="1" inputMode="numeric"
                value={minPrice} onChange={(event) => setMinPrice(event.target.value)} placeholder="최소 가격 입력" className={inputClass} />
              <span aria-hidden="true" className="absolute right-3 top-5 text-sm text-gray-400">원</span>
            </div>
          </div>
        </fieldset>

        <div className="mt-5 border-t border-gray-200 pt-4">
          <label htmlFor={`${id}-manual`} className="text-sm font-medium text-gray-700">가격 수정</label>
          <div className="relative">
            <input id={`${id}-manual`} type="number" min="1" max="2147483647" step="1" inputMode="numeric" disabled={automatic || saving}
              value={manualPrice} onChange={(event) => { setManualPrice(event.target.value); setManualPriceEdited(true); }} placeholder="수정할 가격 입력"
              aria-describedby={`${id}-manual-help`} className={inputClass} />
            <span aria-hidden="true" className="absolute right-3 top-5 text-sm text-gray-400">원</span>
          </div>
          <p id={`${id}-manual-help`} className="mt-2 text-xs text-gray-500">
            {automatic ? "자동 가격 알고리즘을 해제하면 직접 가격을 입력할 수 있습니다." : "자동 가격 알고리즘을 사용하지 않을 때 직접 가격을 입력할 수 있습니다."}
          </p>
        </div>
        {error && <p role="alert" className="mt-3 text-sm text-red-600">{error}</p>}
        {saved && <p role="status" className="mt-3 text-sm text-green-700">가격 설정을 저장했습니다.</p>}
        <button type="submit" disabled={saving} className="mt-4 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:cursor-not-allowed disabled:opacity-50">
          {saving ? "저장 중..." : "설정 저장"}
        </button>
      </form>
    </section>
  );
}
