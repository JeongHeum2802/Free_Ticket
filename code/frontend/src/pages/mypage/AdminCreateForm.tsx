import { useState, type FormEvent, type ChangeEvent } from "react";
import { isAxiosError } from "axios";
import { updateAdminTable, userChoices, type AdminTablePage } from "../../api/admin";

export default function AdminCreateForm({ table, data, disabled, onPending, onCreated }: {
  table: string; data: AdminTablePage; disabled: boolean;
  onPending: (pending: boolean) => void; onCreated: (data: AdminTablePage) => void;
}) {
  const defaults: Record<string, string> = table === "users" ? { role: "USER", status: "ACTIVE" } : {};
  const [values, setValues] = useState<Record<string, string>>(defaults);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const fields = data.createFields ?? data.columns.filter(name => name !== "id").map(name => ({ name, type: "text", required: true }));
  const complete = fields.every(field => !field.required || Boolean(values[field.name]?.trim()));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!complete || disabled || pending) return;
    setPending(true); onPending(true); setError(""); setSuccess(false);
    try {
      const creation = Object.fromEntries(Object.entries(values).filter(([, value]) => value !== ""));
      const result = await updateAdminTable(table, [], data.page, [creation], []);
      setValues(defaults); setSuccess(true); onCreated(result);
    } catch (err) {
      setError(isAxiosError<{ message?: string }>(err) && err.response?.data.message
        ? err.response.data.message : "추가하지 못했습니다. 입력값을 확인하고 다시 시도해 주세요.");
    } finally {
      setPending(false); onPending(false);
    }
  }

  return <form onSubmit={submit} className="mt-8 rounded-xl border border-gray-200 bg-gray-50 p-5" aria-label="새 데이터 추가">
    <h2 className="text-lg font-semibold">{table} 추가</h2>
    <p className="mt-1 text-sm text-gray-500">필수 항목(*)을 입력한 후 추가를 누르세요. ID는 자동 생성됩니다.</p>
    {disabled && !pending && <p className="mt-2 text-sm text-amber-700">표의 변경 사항을 먼저 저장하거나 취소해 주세요.</p>}
    <fieldset disabled={disabled || pending} className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
      {fields.map(field => {
        const choices = userChoices(table, field.name);
        const inputId = `create-${table}-${field.name}`;
        const common = { id: inputId, name: field.name, required: field.required, value: values[field.name] ?? "",
          className: "w-full rounded-lg border border-gray-300 bg-white px-3 py-2 disabled:opacity-50",
          onChange: (event: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
            setValues({ ...values, [field.name]: event.target.value }); setSuccess(false);
          } };
        return <div key={field.name}>
          <label htmlFor={inputId} className="mb-1 block text-sm font-medium">{field.name === "password" ? "초기 비밀번호" : field.name}{field.required ? " *" : ""}</label>
          {choices ? <select {...common}>{choices.map(choice => <option key={choice} value={choice}>{choice}</option>)}</select>
            : field.name.includes("description") ? <textarea {...common} rows={3} />
            : <input {...common} type={field.name === "email" ? "email" : field.type} step="any" autoComplete={field.type === "password" ? "new-password" : "off"} />}
        </div>;
      })}
    </fieldset>
    {error && <p role="alert" className="mt-3 text-sm text-red-600">{error}</p>}
    {success && <p role="status" className="mt-3 text-sm text-green-700">추가한 데이터가 표에 반영되었습니다.</p>}
    <button type="submit" disabled={!complete || disabled || pending} className="mt-5 rounded-lg bg-black px-5 py-2 text-white disabled:opacity-40">{pending ? "추가 중..." : "추가"}</button>
  </form>;
}
