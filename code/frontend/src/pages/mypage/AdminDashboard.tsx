import { Navigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { isAxiosError } from "axios";
import { useAuth } from "../../context/AuthContext";
import AdminCreateForm from "./AdminCreateForm";
import { getAdminTable, getAdminTables, updateAdminTable, userChoices, type AdminTablePage, type AdminRowChange } from "../../api/admin";

const buttonClass = "rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40";
const tableLabels: Record<string, string> = {
  users: "회원", events: "공연", event_directors: "공연 담당자",
  tickets: "티켓", ticket_price_history: "티켓 가격 이력", orders: "주문", payments: "결제",
};

export default function AdminDashboard() {
  const { user, loading } = useAuth();

  if (loading) {
    return <div>로딩 중...</div>;
  }

  if (user?.role !== "ADMIN") {
    return <Navigate to="/mypage/tickets" replace />;
  }

  return (
    <section>
      <h1 className="text-2xl font-bold">관리자 대시보드</h1>
      <p className="mt-2 text-sm text-gray-500">표의 변경은 수정 버튼으로 저장하고, 새 데이터는 아래 입력 폼에서 추가하세요.</p>
      <TableBrowser key={user.id} />
    </section>
  );
}

function TableBrowser() {
  const [tables, setTables] = useState<string[] | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState("");
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [locked, setLocked] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getAdminTables(controller.signal).then(result => {
      if (!controller.signal.aborted) {
        setTables(result);
        setSelected(result[0] ?? "");
      }
    }).catch(() => {
      if (!controller.signal.aborted) setError(true);
    });
    return () => controller.abort();
  }, [attempt]);

  if (error) return <div className="mt-6">
    <p role="alert" className="mb-3 text-red-600">테이블 목록을 불러오지 못했습니다.</p>
    <button className={buttonClass} onClick={() => { setError(false); setAttempt(attempt + 1); }}>다시 시도</button>
  </div>;
  if (!tables) return <p role="status" className="mt-6">테이블 목록을 불러오는 중...</p>;
  if (!tables.length) return <p className="mt-6">조회할 테이블이 없습니다.</p>;

  return <div className="mt-6">
    <div className="mb-5 flex flex-wrap items-end gap-3">
      <div className="flex min-w-56 flex-1 flex-col gap-2">
        <label htmlFor="admin-table" className="text-sm font-medium">테이블 선택</label>
        <select id="admin-table" value={selected} disabled={locked} className="rounded-lg border border-gray-300 bg-white px-3 py-2 disabled:opacity-50"
          onChange={event => { setSelected(event.target.value); setPage(0); }}>
          {tables.map(table => <option key={table} value={table}>{table} · {tableLabels[table] ?? table}</option>)}
        </select>
      </div>
      <button className={buttonClass} disabled={locked} onClick={() => setRefresh(refresh + 1)}>새로고침</button>
    </div>
    <TableRows key={`${selected}:${page}:${refresh}`} table={selected} page={page} onPageChange={setPage} onLockChange={setLocked} />
  </div>;
}

function TableRows({ table, page, onPageChange, onLockChange }: {
  table: string; page: number; onPageChange: (page: number) => void; onLockChange: (locked: boolean) => void;
}) {
  const [data, setData] = useState<AdminTablePage | null>(null);
  const [error, setError] = useState(false);
  const [draft, setDraft] = useState<(string | null)[][]>([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [saved, setSaved] = useState(false);
  const [adding, setAdding] = useState(false);
  const [deleted, setDeleted] = useState<number[]>([]);
  const hasEdits = data !== null && draft.some((row, r) => row.some((value, c) => value !== data.rows[r][c]));
  const dirty = hasEdits || deleted.length > 0;

  useEffect(() => {
    if (!dirty && !saving && !adding) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, saving, adding]);

  useEffect(() => {
    const controller = new AbortController();
    getAdminTable(table, page, controller.signal).then(result => {
      if (!controller.signal.aborted) { setData(result); setDraft(result.rows); }
    }).catch(() => {
      if (!controller.signal.aborted) setError(true);
    });
    return () => controller.abort();
  }, [table, page]);

  if (error) return <p role="alert" className="text-red-600">데이터를 불러오지 못했습니다. 새로고침으로 다시 시도해 주세요.</p>;
  if (!data) return <p role="status">데이터를 불러오는 중...</p>;
  const totalPages = Math.max(1, Math.ceil(data.totalRows / data.size));
  const idIndex = data.columns.indexOf("id");

  function editCell(rowIndex: number, columnIndex: number, value: string | null) {
    if (!data || saving) return;
    const normalized = value === "" && data.rows[rowIndex][columnIndex] === null ? null : value;
    const next = draft.map((row, r) => r === rowIndex ? row.map((old, c) => c === columnIndex ? normalized : old) : row);
    setDraft(next);
    setSaved(false);
    setSaveError("");
    onLockChange(deleted.length > 0 || next.some((row, r) => row.some((cell, c) => cell !== data.rows[r][c])));
  }

  function toggleDelete(rowIndex: number) {
    const next = deleted.includes(rowIndex) ? deleted.filter(index => index !== rowIndex) : [...deleted, rowIndex];
    setDeleted(next); setSaved(false); setSaveError("");
    onLockChange(hasEdits || next.length > 0);
  }

  async function save() {
    if (!data || !dirty || saving) return;
    const changes: AdminRowChange[] = [];
    draft.forEach((row, r) => {
      if (deleted.includes(r)) return;
      const values: AdminRowChange["values"] = {};
      const originalValues: AdminRowChange["originalValues"] = {};
      row.forEach((value, c) => {
        if (value !== data.rows[r][c]) {
          values[data.columns[c]] = value;
          originalValues[data.columns[c]] = data.rows[r][c];
        }
      });
      if (Object.keys(values).length) changes.push({ id: data.rows[r][idIndex]!, originalValues, values });
    });
    setSaving(true);
    setSaveError("");
    try {
      const deletions = deleted.map(r => ({ id: data.rows[r][idIndex]!,
        originalValues: Object.fromEntries(data.columns.map((column, c) => [column, data.rows[r][c]])) }));
      const result = await updateAdminTable(table, changes, page, [], deletions);
      setData(result);
      setDraft(result.rows);
      setSaved(true);
      setDeleted([]);
      onLockChange(false);
      if (result.page !== page) onPageChange(result.page);
    } catch (err) {
      setSaveError(isAxiosError<{ message?: string }>(err) && err.response?.data.message
        ? err.response.data.message : "저장하지 못했습니다. 입력값을 확인하고 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  }

  return <div>
    <div className="mb-4 flex flex-wrap items-center gap-3">
      <button className={`${buttonClass} bg-black text-white enabled:hover:bg-gray-800`} disabled={!dirty || saving} onClick={save}>
        {saving ? "수정 중..." : "수정"}
      </button>
      <button className={buttonClass} disabled={!dirty || saving} onClick={() => {
        setDraft(data.rows); setDeleted([]); setSaveError(""); setSaved(false); onLockChange(false);
      }}>변경 취소</button>
      {dirty && <span className="text-sm text-amber-700">저장하지 않은 변경 사항이 있습니다.</span>}
      {deleted.length > 0 && <span className="text-sm text-red-600">{deleted.length}행 삭제 예정</span>}
      {saved && <span role="status" className="text-sm text-green-700">수정 사항을 저장했습니다.</span>}
    </div>
    {saveError && <p role="alert" className="mb-4 text-sm text-red-600">{saveError}</p>}
    <p className="mb-3 text-sm text-gray-600">전체 {data.totalRows.toLocaleString()}행 · 페이지당 {data.size}행</p>
    <div className="max-h-[60vh] overflow-auto rounded-lg border border-gray-200" tabIndex={0} aria-label={`${table} 데이터`}>
      <table className="w-full text-left text-sm">
        <caption className="sr-only">{table} 테이블 데이터</caption>
        <thead className="sticky top-0 bg-gray-100">
          <tr>{data.columns.map(column => <th key={column} scope="col" className="whitespace-nowrap border-b border-gray-200 px-4 py-3 font-semibold">{column}</th>)}<th scope="col" className="px-4 py-3">작업</th></tr>
        </thead>
        <tbody>
          {draft.map((row, rowIndex) => <tr key={rowIndex} className={`border-b border-gray-100 last:border-0 ${deleted.includes(rowIndex) ? "bg-red-50 opacity-60" : "hover:bg-gray-50"}`}>
            {row.map((value, index) => <td key={data.columns[index]} className="min-w-28 max-w-96 whitespace-pre-wrap break-words px-4 py-3 align-top">
              {index === idIndex || (table === "users" && data.columns[index] === "customer_key") ? value : <div className="min-w-40">
                {userChoices(table, data.columns[index]) ? <select aria-label={`${data.columns[index]} (ID: ${row[idIndex]})`}
                  value={value ?? ""} disabled={saving || adding || deleted.includes(rowIndex)}
                  className={`w-full rounded border px-2 py-2 ${value !== data.rows[rowIndex][index] ? "border-amber-400 bg-amber-50" : "border-gray-200 bg-white"}`}
                  onChange={event => editCell(rowIndex, index, event.target.value)}>
                  {!userChoices(table, data.columns[index])!.includes(value ?? "") && <option value={value ?? ""} disabled>기존 값: {value ?? "미설정"}</option>}
                  {userChoices(table, data.columns[index])!.map(choice => <option key={choice} value={choice}>{choice}</option>)}
                </select> : <textarea aria-label={`${data.columns[index]} (ID: ${row[idIndex]})`} value={value ?? ""}
                  rows={2} disabled={saving || adding || deleted.includes(rowIndex)}
                  className={`w-full resize-y rounded border px-2 py-1 outline-none focus:border-blue-500 ${value !== data.rows[rowIndex][index] ? "border-amber-400 bg-amber-50" : "border-gray-200 bg-white"}`}
                  onChange={event => editCell(rowIndex, index, event.target.value)} />}
              </div>}
            </td>)}
            <td className="px-4 py-3 align-top"><button className={`${buttonClass} whitespace-nowrap text-red-600`} disabled={saving || adding}
              aria-label={`${deleted.includes(rowIndex) ? "삭제 취소" : "삭제"} (ID: ${row[idIndex]})`}
              onClick={() => toggleDelete(rowIndex)}>{deleted.includes(rowIndex) ? "삭제 취소" : "삭제"}</button></td>
          </tr>)}
          {!data.rows.length && <tr><td colSpan={data.columns.length + 1} className="px-4 py-10 text-center text-gray-500">데이터가 없습니다.</td></tr>}
        </tbody>
      </table>
    </div>
    <div className="mt-4 flex items-center justify-between gap-3">
      <button className={buttonClass} disabled={page === 0 || dirty || saving || adding} onClick={() => onPageChange(page - 1)}>이전</button>
      <span className="text-sm text-gray-600">{page + 1} / {Math.max(totalPages, page + 1)} 페이지</span>
      <button className={buttonClass} disabled={page + 1 >= totalPages || dirty || saving || adding} onClick={() => onPageChange(page + 1)}>다음</button>
    </div>
    {table !== "orders" && table !== "payments" && <AdminCreateForm table={table} data={data} disabled={dirty || saving || adding}
      onPending={pending => { setAdding(pending); onLockChange(pending || dirty); }}
      onCreated={result => { setData(result); setDraft(result.rows); setSaved(false); if (result.page !== page) onPageChange(result.page); }} />}
  </div>;
}
