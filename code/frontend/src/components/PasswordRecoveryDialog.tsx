import { useEffect, useRef, useState } from "react";
import axios from "axios";
import EmailVerification from "./EmailVerification";
import { completePasswordRecovery } from "../api/passwordRecovery";

const inputClass = "w-full rounded-lg border border-gray-300 px-3 py-3 text-base outline-none focus:border-[#1089ff] focus:ring-2 focus:ring-[#1089ff]/20 disabled:bg-gray-100";
const primaryClass = "w-full rounded-lg bg-[#1089ff] px-4 py-3 font-semibold text-white hover:bg-[#0078ed] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#1089ff] disabled:cursor-wait disabled:opacity-60";

export default function PasswordRecoveryDialog({ onClose }: { onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const passwordInput = useRef<HTMLInputElement>(null);
  const doneHeading = useRef<HTMLHeadingElement>(null);
  const active = useRef(true);
  const [email, setEmail] = useState("");
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  useEffect(() => {
    active.current = true;
    const opener = document.activeElement as HTMLElement | null;
    const element = dialog.current;
    if (element?.showModal) element.showModal();
    else element?.setAttribute("open", "");
    element?.querySelector("input")?.focus();
    return () => {
      active.current = false;
      element?.close?.();
      opener?.focus();
    };
  }, []);

  useEffect(() => {
    if (done) doneHeading.current?.focus();
    else if (token) passwordInput.current?.focus();
    else dialog.current?.querySelector("input")?.focus();
  }, [token, done]);

  function restart() {
    setToken("");
    setPassword("");
    setConfirmation("");
    setShowPassword(false);
    setError("");
  }

  async function submit() {
    if (busy) return;
    if ([...password].length < 8 || /[\s\p{Cc}]/u.test(password) || new TextEncoder().encode(password).length > 72) {
      setError("비밀번호는 공백 없이 8자 이상, 영문·숫자 기준 최대 72자로 입력해 주세요. 한글 등은 더 짧을 수 있습니다.");
      return;
    }
    if (password !== confirmation) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await completePasswordRecovery(email.trim(), token, password, confirmation);
      if (!active.current) return;
      setPassword("");
      setConfirmation("");
      setDone(true);
    } catch (failure) {
      if (active.current) setError(axios.isAxiosError(failure)
        ? failure.response?.data?.message ?? "응답을 받지 못했습니다. 새 비밀번호로 로그인해 보거나 이메일 인증부터 다시 진행해 주세요."
        : "응답을 받지 못했습니다. 새 비밀번호로 로그인해 보거나 이메일 인증부터 다시 진행해 주세요.");
    } finally {
      if (active.current) setBusy(false);
    }
  }

  return (
    <dialog ref={dialog} aria-labelledby="recovery-title" aria-describedby="recovery-description"
      className="fixed m-auto max-h-[90dvh] w-full max-w-md overflow-y-auto border-0 bg-transparent p-4 text-[#333] backdrop:bg-black/50"
      onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}
      onKeyDown={event => { if (event.key === "Escape") { event.preventDefault(); if (!busy) onClose(); } }}
      onClick={event => { if (event.target === event.currentTarget && !busy) onClose(); }}>
      <div className="rounded-xl bg-white p-6 sm:p-8">
        <div className="mb-7 flex items-start justify-between gap-4">
          <h2 id="recovery-title" ref={doneHeading} tabIndex={done ? -1 : undefined} className="text-2xl font-bold tracking-tight">
            {done ? "비밀번호를 변경했습니다" : token ? "새 비밀번호 설정" : "비밀번호 찾기"}
          </h2>
          <button type="button" onClick={onClose} disabled={busy} aria-label="비밀번호 찾기 닫기"
            className="-mr-2 -mt-2 p-2 text-sm text-gray-600 hover:text-black disabled:opacity-50">닫기</button>
        </div>

        <p id="recovery-description" className="mb-7 text-sm leading-6 text-gray-600">
          {done ? "새 비밀번호로 FreeTicket에 로그인해 주세요."
            : token ? `${email.trim()} 인증을 완료했습니다. 새로운 비밀번호를 입력해 주세요.`
            : "가입한 이메일로 인증번호를 받아 비밀번호를 다시 설정할 수 있습니다."}
        </p>

        {done ? <button type="button" className={primaryClass} onClick={onClose}>로그인으로 돌아가기</button>
          : token ? (
            <form noValidate onSubmit={event => { event.preventDefault(); void submit(); }} className="space-y-5" aria-busy={busy}>
              <div>
                <label htmlFor="recovery-password" className="mb-2 block text-sm font-medium">새 비밀번호</label>
                <input ref={passwordInput} id="recovery-password" type={showPassword ? "text" : "password"}
                  autoComplete="new-password" value={password} disabled={busy} aria-describedby="recovery-password-help"
                  className={inputClass} onChange={event => setPassword(event.target.value)} />
                <p id="recovery-password-help" className="mt-2 text-xs leading-5 text-gray-600">
                  공백 없이 8자 이상 입력해 주세요. 영문·숫자는 최대 72자이며, 한글 등은 더 짧을 수 있습니다. 이전 비밀번호는 사용할 수 없습니다.
                </p>
              </div>
              <div>
                <label htmlFor="recovery-confirmation" className="mb-2 block text-sm font-medium">비밀번호 확인</label>
                <input id="recovery-confirmation" type={showPassword ? "text" : "password"} autoComplete="new-password"
                  value={confirmation} disabled={busy} className={inputClass} onChange={event => setConfirmation(event.target.value)} />
              </div>
              <label className="flex items-center gap-2 text-sm text-gray-600">
                <input type="checkbox" checked={showPassword} onChange={event => setShowPassword(event.target.checked)} />비밀번호 표시
              </label>
              {error && <p role="alert" className="text-sm leading-6 text-red-700">{error}</p>}
              <button type="submit" disabled={busy} className={primaryClass}>{busy ? "변경 중…" : "비밀번호 변경"}</button>
              <button type="button" disabled={busy} onClick={restart} className="w-full py-2 text-sm text-gray-600 underline underline-offset-4">이메일 다시 인증하기</button>
            </form>
          ) : (
            <div className="space-y-5">
              <div>
                <label htmlFor="recovery-email" className="mb-2 block text-sm font-medium">가입 이메일</label>
                <input id="recovery-email" type="email" autoComplete="email" maxLength={254}
                  placeholder="example@email.com" value={email} className={inputClass}
                  onChange={event => setEmail(event.target.value)} />
              </div>
              <EmailVerification key={email} email={email.trim()} purpose="password-reset" onVerified={setToken} />
              <p className="text-xs leading-5 text-gray-600">인증번호가 도착하지 않으면 스팸 메일함을 확인해 주세요. 재발송은 1분 후 가능합니다.</p>
            </div>
          )}
      </div>
    </dialog>
  );
}
