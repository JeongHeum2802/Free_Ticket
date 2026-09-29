import { useEffect, useState, type FormEvent } from "react";
import { isAxiosError } from "axios";
import { getAdminReconciliationIssue, getAdminReconciliationIssues, performAdminReconciliationAction,
  type ReconciliationDetail, type ReconciliationFilters, type ReconciliationPage } from "../../api/admin";

const buttonClass = "rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40";
const inputClass = "w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm";
const issueTypes: Record<string, { label: string; description: string }> = {
  PAID_PG_CANCELED: { label: "PG 전액 취소", description: "발견 당시 토스에서는 전액 취소됐지만 내부 주문은 결제 완료 상태였습니다." },
  PAID_PG_PARTIAL_CANCELED: { label: "PG 부분 취소", description: "발견 당시 토스에 부분 취소 거래가 있었지만 내부 주문은 결제 완료 상태였습니다." },
  PAYMENT_IDENTITY_MISMATCH: { label: "결제 식별 정보 불일치", description: "발견 당시 토스 거래와 내부 주문·결제의 결제 식별 정보가 일치하지 않았습니다." },
  LOCAL_PAYMENT_INCONSISTENT: { label: "내부 결제 기록 불일치", description: "발견 당시 결제 기록이 없거나 결제 상태·금액이 내부 주문과 일치하지 않았습니다." },
  UNMATCHED_PG_CANCELLATION: { label: "내부 주문 없음", description: "발견 당시 토스 취소 거래에 해당하는 내부 주문을 찾지 못했습니다." },
};
const statusLabels: Record<string, string> = {
  PAID: "결제 완료", DONE: "승인 완료", CANCELED: "취소 완료", PARTIAL_CANCELED: "부분 취소",
  PENDING: "결제 대기", CONFIRMING: "승인 확인 중", CANCELING: "취소 확인 중",
  EXPIRED: "만료", PAYMENT_FAILED: "결제 실패",
};
const koreanTime = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
  hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
});
const statusLabel = (status: string | null) => status ? statusLabels[status] ?? status : "기록 없음";
const pgLabel = (status: string) => status === "CANCELED" ? "전액 취소" : statusLabel(status);
const money = (amount: number) => `${amount.toLocaleString("ko-KR")}원`;

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error)) {
    if (error.response?.data?.code === "ADMIN_REQUIRED") return "현재 계정에 관리자 권한이 없습니다.";
    if (typeof error.response?.data?.message === "string") return error.response.data.message;
  }
  return fallback;
}

export default function AdminPaymentReconciliation() {
  const [filters, setFilters] = useState({ orderId: "", issueType: "", resolution: "", from: "", to: "" });
  const [query, setQuery] = useState<ReconciliationFilters>({});
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [filterError, setFilterError] = useState("");

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (filters.from && filters.to && filters.from > filters.to) {
      setFilterError("발견일 종료는 시작일보다 빠를 수 없습니다.");
      return;
    }
    setFilterError("");
    setQuery({
      orderId: filters.orderId.trim() || undefined,
      issueType: filters.issueType || undefined,
      ...(filters.resolution ? { resolution: filters.resolution } : {}),
      from: filters.from ? new Date(`${filters.from}T00:00:00+09:00`).toISOString() : undefined,
      to: filters.to ? new Date(new Date(`${filters.to}T00:00:00+09:00`).getTime() + 86400000).toISOString() : undefined,
    });
    setPage(0);
    setRefresh(value => value + 1);
  }

  return <section>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 className="text-xl font-semibold">결제 불일치 기록</h2>
      <button className={buttonClass} onClick={() => setRefresh(value => value + 1)}>새로고침</button>
    </div>
    <p className="mt-2 text-sm text-gray-600">토스 상태는 발견 당시 기록입니다. 새로고침하면 저장된 기록과 현재 내부 상태를 다시 읽습니다.</p>
    <p className="mt-1 text-sm text-gray-500">시각과 검색 날짜는 한국시간 기준입니다. 같은 주문에 여러 취소 거래가 기록될 수 있습니다.</p>
    {selectedId === null ? <>
      <form onSubmit={search} className="my-5 grid gap-3 sm:grid-cols-2">
        <label className="text-sm">주문번호<input className={`${inputClass} mt-1`} value={filters.orderId} maxLength={64}
          onChange={event => setFilters({ ...filters, orderId: event.target.value })} /></label>
        <label className="text-sm">불일치 유형<select className={`${inputClass} mt-1`} value={filters.issueType}
          onChange={event => setFilters({ ...filters, issueType: event.target.value })}>
          <option value="">전체</option>
          {Object.entries(issueTypes).map(([type, { label }]) => <option key={type} value={type}>{label}</option>)}
        </select></label>
        <label className="text-sm">처리 상태<select className={`${inputClass} mt-1`} value={filters.resolution}
          onChange={event => setFilters({ ...filters, resolution: event.target.value })}>
          <option value="">전체</option><option value="OPEN">미해결</option><option value="RESOLVED">해결 완료</option>
        </select></label>
        <label className="text-sm">발견일 시작<input type="date" className={`${inputClass} mt-1`} value={filters.from}
          onChange={event => setFilters({ ...filters, from: event.target.value })} /></label>
        <label className="text-sm">발견일 종료<input type="date" className={`${inputClass} mt-1`} value={filters.to}
          onChange={event => setFilters({ ...filters, to: event.target.value })} /></label>
        <button className={`${buttonClass} sm:col-span-2`} type="submit">검색</button>
      </form>
      {filterError && <p role="alert" className="mb-3 text-red-600">{filterError}</p>}
      <IssueList key={`${JSON.stringify(query)}:${page}:${refresh}`} query={query} page={page}
        onPageChange={setPage} onSelect={setSelectedId} />
    </> : <IssueDetail key={`${selectedId}:${refresh}`} id={selectedId} onClose={() => setSelectedId(null)} />}
  </section>;
}

function IssueList({ query, page, onPageChange, onSelect }: {
  query: ReconciliationFilters; page: number; onPageChange: (page: number) => void; onSelect: (id: number) => void;
}) {
  const [data, setData] = useState<ReconciliationPage | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    getAdminReconciliationIssues(query, page, controller.signal).then(result => {
      if (!controller.signal.aborted) setData(result);
    }).catch(error => {
      if (!controller.signal.aborted) setError(errorMessage(error, "불일치 목록을 불러오지 못했습니다. 새로고침으로 다시 시도해 주세요."));
    });
    return () => controller.abort();
  }, [query, page]);

  if (error) return <p role="alert" className="text-red-600">{error}</p>;
  if (!data) return <p role="status">불일치 목록을 불러오는 중...</p>;
  return <div>
    <p className="mb-3 text-sm text-gray-600">전체 {data.totalElements.toLocaleString()}건 · 페이지당 {data.size}건</p>
    <div className="overflow-auto rounded-lg border border-gray-200" tabIndex={0} aria-label="결제 불일치 목록">
      <table className="w-full whitespace-nowrap text-left text-sm">
        <caption className="sr-only">발견 당시 결제 불일치 목록</caption>
        <thead className="bg-gray-100"><tr>
          {["발견 시각", "주문번호", "불일치 유형", "토스 상태", "내부 주문·결제 상태", "처리 상태", "작업"].map(label =>
            <th key={label} scope="col" className="px-4 py-3">{label}</th>)}
        </tr></thead>
        <tbody>{data.items.map(issue => <tr key={issue.id} className="border-t border-gray-100">
          <td className="px-4 py-3">{koreanTime.format(new Date(issue.detectedAtUtc))}</td>
          <td className="px-4 py-3">{issue.orderId}</td>
          <td className="px-4 py-3">{issueTypes[issue.issueType]?.label ?? issue.issueType}</td>
          <td className="px-4 py-3">{pgLabel(issue.pgStatus)}</td>
          <td className="px-4 py-3">{statusLabel(issue.dbOrderStatus)} / {statusLabel(issue.dbPaymentStatus)}</td>
          <td className="px-4 py-3">{issue.resolvedAtUtc ? "해결 완료" : "미해결"}</td>
          <td className="px-4 py-3"><button className={buttonClass} aria-label={`상세 보기 ${issue.orderId}`}
            onClick={() => onSelect(issue.id)}>상세 보기</button></td>
        </tr>)}</tbody>
      </table>
    </div>
    {!data.items.length && <p className="py-6 text-center text-gray-500">검색 조건에 해당하는 기록이 없습니다.</p>}
    <div className="mt-4 flex items-center justify-between gap-3">
      <button className={buttonClass} disabled={page === 0} onClick={() => onPageChange(page - 1)}>이전</button>
      <span className="text-sm text-gray-600">{page + 1} / {Math.max(data.totalPages, page + 1)} 페이지</span>
      <button className={buttonClass} disabled={page + 1 >= data.totalPages} onClick={() => onPageChange(page + 1)}>다음</button>
    </div>
  </div>;
}

function IssueDetail({ id, onClose }: { id: number; onClose: () => void }) {
  const [data, setData] = useState<ReconciliationDetail | null>(null);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    getAdminReconciliationIssue(id, controller.signal).then(result => {
      if (!controller.signal.aborted) setData(result);
    }).catch(error => {
      if (!controller.signal.aborted) setError(errorMessage(error, "상세 기록을 불러오지 못했습니다. 새로고침으로 다시 시도해 주세요."));
    });
    return () => controller.abort();
  }, [id]);

  async function perform(operation: "recheck" | "apply-full-cancellation" | "notes") {
    if (!reason.trim()) { setActionError("처리·조사 사유를 입력해 주세요."); return; }
    setBusy(true); setActionError(""); setMessage("");
    try {
      setData(await performAdminReconciliationAction(id, operation, reason.trim()));
      setConfirming(false);
      setMessage(operation === "recheck" ? "토스 상태를 확인하고 기록했습니다."
        : operation === "notes" ? "조사 사유를 기록했습니다." : "전액 취소를 반영했습니다.");
    } catch (error) {
      setActionError(errorMessage(error, "처리를 완료하지 못했습니다. 다시 조회한 뒤 재시도해 주세요."));
      setConfirming(false);
      // 실패 이력과 현재 상태를 다시 읽어 오래된 확인 결과를 갱신한다.
      try { setData(await getAdminReconciliationIssue(id)); } catch { /* 원래 처리 오류를 유지한다. */ }
    } finally { setBusy(false); }
  }

  return <section aria-label="결제 불일치 상세" className="mt-5">
    <button className={buttonClass} disabled={busy} onClick={onClose}>목록으로</button>
    {error ? <p role="alert" className="mt-4 text-red-600">{error}</p> : !data ? <p role="status" className="mt-4">상세 기록을 불러오는 중...</p> : <>
      <h3 className="mt-4 text-lg font-semibold">{data.issue.orderId}</h3>
      <p className="mt-2 text-sm font-medium">{data.issue.resolvedAtUtc ? "해결 완료" : "미해결"}</p>
      {data.issue.resolvedAtUtc && <p className="mt-1 text-xs text-gray-500">
        {koreanTime.format(new Date(data.issue.resolvedAtUtc))} · 관리자 #{data.issue.resolvedBy}</p>}
      <p className="mt-2 text-sm text-gray-600">{issueTypes[data.issue.issueType]?.description ?? data.issue.issueType}</p>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-gray-200 p-4">
          <h4 className="mb-3 font-semibold">발견 당시 기록</h4>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-sm">
            <dt>토스 상태</dt><dd>{pgLabel(data.issue.pgStatus)}</dd>
            <dt>주문 상태</dt><dd>{statusLabel(data.issue.dbOrderStatus)}</dd>
            <dt>결제 상태</dt><dd>{statusLabel(data.issue.dbPaymentStatus)}</dd>
            <dt>거래 시각</dt><dd>{koreanTime.format(new Date(data.issue.transactionAtUtc))}</dd>
            <dt>발견 시각</dt><dd>{koreanTime.format(new Date(data.issue.detectedAtUtc))}</dd>
            <dt>기록 번호</dt><dd>{data.issue.id}</dd>
          </dl>
        </div>
        <div className="rounded-lg border border-gray-200 p-4">
          <h4 className="mb-3 font-semibold">현재 내부 상태</h4>
          {data.currentOrder ? <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-sm">
            <dt>주문 상태</dt><dd>{statusLabel(data.currentOrder.status)}</dd>
            <dt>구매 수량</dt><dd>{data.currentOrder.quantity}매</dd>
            <dt>주문 금액</dt><dd>{money(data.currentOrder.totalAmount)}</dd>
          </dl> : <p className="text-sm text-amber-700">주문을 찾을 수 없습니다.</p>}
          {data.currentPayment ? <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-sm">
            <dt>결제 상태</dt><dd>{statusLabel(data.currentPayment.status)}</dd>
            <dt>결제 금액</dt><dd>{money(data.currentPayment.amount)}</dd>
          </dl> : <p className="mt-3 text-sm text-amber-700">결제 기록을 찾을 수 없습니다.</p>}
          <p className="mt-4 text-xs text-gray-500">조회 시각: {koreanTime.format(new Date(data.checkedAtUtc))}</p>
        </div>
      </div>
      <div className="mt-4 rounded-lg border border-gray-200 p-4">
        <h4 className="mb-3 font-semibold">토스 재확인 결과</h4>
        {data.latestCheck ? <>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-sm">
            <dt>토스 상태</dt><dd>{pgLabel(data.latestCheck.status)}</dd>
            <dt>결제 금액</dt><dd>{data.latestCheck.totalAmount === null ? "확인 불가" : money(data.latestCheck.totalAmount)}</dd>
            <dt>취소 잔액</dt><dd>{data.latestCheck.balanceAmount === null ? "확인 불가" : money(data.latestCheck.balanceAmount)}</dd>
            <dt>식별 정보·금액</dt><dd>{data.latestCheck.identityMatches ? "일치" : "확인 필요"}</dd>
            <dt>확인 시각</dt><dd>{koreanTime.format(new Date(data.latestCheck.checkedAtUtc))}</dd>
          </dl>
          {!!data.latestCheck.cancels.length && <ul className="mt-3 space-y-1 text-sm">
            {data.latestCheck.cancels.map((cancel, index) => <li key={index}>
              취소 {index + 1}: {money(cancel.amount)} · {cancel.status === "DONE" ? "취소 완료" : statusLabel(cancel.status)}
              {cancel.canceledAt && ` · ${koreanTime.format(new Date(cancel.canceledAt))}`}
            </li>)}
          </ul>}
          {data.latestCheck.blockedReason && <p className="mt-3 text-sm text-amber-700">{data.latestCheck.blockedReason}</p>}
        </> : <p className="text-sm text-gray-500">PG 상태 재확인으로 현재 토스 상태와 취소 내역을 확인해 주세요.</p>}
      </div>
      <div className="mt-4 rounded-lg border border-gray-200 p-4">
        <label className="block text-sm font-medium">처리·조사 사유
          <textarea className={`${inputClass} mt-2`} rows={3} maxLength={500} value={reason} disabled={busy}
            onChange={event => setReason(event.target.value)} />
        </label>
        <div className="mt-3 flex flex-wrap gap-2">
          <button className={buttonClass} disabled={busy} onClick={() => perform("recheck")}>PG 상태 재확인</button>
          <button className={buttonClass} disabled={busy} onClick={() => perform("notes")}>조사 사유 기록</button>
          <button className={buttonClass} disabled={busy || !!data.issue.resolvedAtUtc || !data.latestCheck?.canApplyFullCancellation}
            onClick={() => { setActionError(""); setConfirming(true); }}>전액 취소 반영</button>
        </div>
        <p className="mt-3 text-xs text-gray-500">취소 반영 시 토스를 다시 조회하고, 내부 주문·결제를 취소 처리하며 구매 수량만큼 판매 수량을 줄입니다.</p>
        {confirming && <div className="mt-3 rounded-lg bg-amber-50 p-3" role="group" aria-label="전액 취소 반영 확인">
          <p className="text-sm">{data.issue.orderId}의 {data.currentOrder?.quantity}매 예매를 취소 처리합니다. 계속하려면 확정해 주세요.</p>
          <div className="mt-3 flex gap-2">
            <button className={`${buttonClass} border-red-300 text-red-700`} disabled={busy}
              onClick={() => perform("apply-full-cancellation")}>취소 반영 확정</button>
            <button className={buttonClass} disabled={busy} onClick={() => setConfirming(false)}>돌아가기</button>
          </div>
        </div>}
        {busy && <p role="status" className="mt-3 text-sm">처리 중입니다...</p>}
        {actionError && <p role="alert" className="mt-3 text-red-600">{actionError}</p>}
        {message && <p role="status" className="mt-3 text-sm text-green-700">{message}</p>}
      </div>
      <div className="mt-4 rounded-lg border border-gray-200 p-4">
        <h4 className="font-semibold">처리 이력</h4>
        <p className="mt-1 text-xs text-gray-500">최근 50건을 표시합니다. 조사 기록만으로는 해결 완료 처리되지 않습니다.</p>
        {!data.history.length ? <p className="mt-3 text-sm text-gray-500">처리 이력이 없습니다.</p> : <ol className="mt-3 space-y-3">
          {data.history.map(action => <li key={action.id} className="rounded-lg bg-gray-50 p-3 text-sm">
            <p className="font-medium">{{ RECHECK: "PG 재확인", NOTE: "조사 사유 기록", APPLY_FULL_CANCELLATION: "전액 취소 반영" }[action.action] ?? action.action}
              {" · "}{{ SUCCESS: "성공", FAILED: "실패", NO_CHANGE: "이미 반영됨" }[action.result] ?? action.result}</p>
            <p className="mt-1 text-xs text-gray-500">{koreanTime.format(new Date(action.occurredAtUtc))} · 관리자 #{action.actorId}</p>
            <p className="mt-2 whitespace-pre-wrap break-words">{action.reason}</p>
            {action.errorCode && <p className="mt-1 text-red-600">처리 실패 · 다음 시도 전 상태를 다시 확인해 주세요.</p>}
            {(action.beforeOrderStatus || action.afterOrderStatus) && <p className="mt-2 text-xs text-gray-600">
              주문: {statusLabel(action.beforeOrderStatus)} → {statusLabel(action.afterOrderStatus)}
              {" · "}결제: {statusLabel(action.beforePaymentStatus)} → {statusLabel(action.afterPaymentStatus)}</p>}
            {action.soldBefore !== null && action.soldAfter !== null && <p className="mt-1 text-xs text-gray-600">
              판매 수량: {action.soldBefore}매 → {action.soldAfter}매</p>}
            {action.pg && <details className="mt-2 text-xs text-gray-600"><summary className="cursor-pointer">당시 토스 확인 근거</summary>
              <p className="mt-1">{pgLabel(action.pg.status)} · 잔액 {action.pg.balanceAmount === null ? "확인 불가" : money(action.pg.balanceAmount)}
                {" · "}{koreanTime.format(new Date(action.pg.checkedAtUtc))}</p>
            </details>}
          </li>)}
        </ol>}
      </div>
    </>}
  </section>;
}
