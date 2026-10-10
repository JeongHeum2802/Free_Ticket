import { useEffect, useId, useRef, useState } from "react";
import axios from "axios";
import { sendEmailVerification, verifyEmail } from "../api/emailVerification";

export default function EmailVerification({ email, onVerified }: {
  email: string;
  onVerified: (token: string) => void;
}) {
  const [token, setToken] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);
  const [message, setMessage] = useState("");
  const active = useRef(true);
  const codeId = useId();

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  function showError(error: unknown) {
    if (active.current) setMessage(axios.isAxiosError(error)
      ? error.response?.data?.message ?? "요청에 실패했습니다. 다시 시도해 주세요."
      : "요청에 실패했습니다. 다시 시도해 주세요.");
  }

  async function send() {
    setBusy(true);
    setVerified(false);
    onVerified("");
    setMessage("");
    try {
      const result = await sendEmailVerification(email);
      if (!active.current) return;
      setToken(result.verificationToken);
      setCode("");
      setMessage(result.message);
    } catch (error) {
      showError(error);
    } finally {
      if (active.current) setBusy(false);
    }
  }

  async function verify() {
    setBusy(true);
    try {
      const result = await verifyEmail(email, token, code);
      if (!active.current) return;
      setVerified(true);
      onVerified(token);
      setMessage(result.message);
    } catch (error) {
      showError(error);
    } finally {
      if (active.current) setBusy(false);
    }
  }

  return (
    <div className="space-y-2 text-sm">
      <button type="button" onClick={send} disabled={busy || !email}
        className="rounded-lg border border-gray-300 px-3 py-2 disabled:opacity-50">
        {busy ? "처리 중…" : token ? "인증번호 다시 보내기" : "인증번호 보내기"}
      </button>
      {token && !verified && <div className="flex items-end gap-2">
        <div className="min-w-0 flex-1">
          <label htmlFor={codeId} className="mb-1 block">이메일 인증번호</label>
          <input id={codeId} value={code} inputMode="numeric" autoComplete="one-time-code"
            maxLength={6} onChange={event => setCode(event.target.value.replace(/\D/g, ""))}
            className="w-full rounded-lg border border-gray-300 px-3 py-2" />
        </div>
        <button type="button" onClick={verify} disabled={busy || code.length !== 6}
          className="shrink-0 rounded-lg bg-black px-3 py-2 text-white disabled:opacity-50">인증 확인</button>
      </div>}
      <p role="status" className="text-gray-600">{message}</p>
    </div>
  );
}
