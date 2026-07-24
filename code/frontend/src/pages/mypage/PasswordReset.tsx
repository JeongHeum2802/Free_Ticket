import { useState } from "react";
import { resetPasswordApi } from "../../api/auth";
import axios from "axios";

export default function PasswordReset() {
  const [currentPassword, setCurrentPassword] = useState<string>("");
  const [newPassword, setNewPassword] = useState<string>("");
  const [newPasswordRe, setNewPasswordRe] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleChangeCurrentPassword = (value: string) => {
    setCurrentPassword(value);
  }

  const handleChangeNewPassword = (value: string) => {
    setNewPassword(value);
  }

  const handleChangeNewPasswordRe = (value: string) => {
    setNewPasswordRe(value);
  }

  const handleClickResetPassword = async () => {

    if (newPassword.trim() === "" ||
      currentPassword.trim() === "" ||
      newPasswordRe.trim() === "") {
      setErrorMessage("비밀번호를 채우셔야 합니다.");
      return;
    }

    if (newPassword.trim().length < 8 ||
      currentPassword.trim().length < 8 ||
      newPasswordRe.trim().length < 8) {
      setErrorMessage("비밀번호는 8자 이상이어야 합니다.");
      return;
    }

    if (newPassword !== newPasswordRe) {
      setErrorMessage("재입력이 일치하지 않습니다.");
      return;
    }

    try {
      const response = await resetPasswordApi({
        currentPassword: currentPassword,
        newPassword: newPassword,
      });
      alert(response.message);

      setCurrentPassword("");
      setNewPassword("");
      setNewPasswordRe("");
      setErrorMessage(null);
    } catch (error) {
      if (axios.isAxiosError(error)) {
        let message = error.response?.data?.message ?? "예상치 못한 오류가 발생했습니다.";
        setErrorMessage(message);
      }
    }
  }

  return (
    <section>
      <h1 className="text-2xl font-bold">비밀번호 재설정</h1>
      <p className="mt-2 text-sm text-gray-500">
        비밀번호를 재설정 할 수 있습니다.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleClickResetPassword();
        }}
        className="mt-6 max-w-xl space-y-5">

        <div>
          <label
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            현재 비밀번호
          </label>
          <input
            value={currentPassword}
            type="password"
            onChange={(e) => handleChangeCurrentPassword(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black"
          />
        </div>

        <div>
          <label
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            새 비밀번호
          </label>
          <input
            value={newPassword}
            type="password"
            onChange={(e) => handleChangeNewPassword(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black"
          />
        </div>

        <div>
          <label
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            새 비밀번호 재입력
          </label>
          <input
            value={newPasswordRe}
            type="password"
            onChange={(e) => handleChangeNewPasswordRe(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black"
          />
        </div>

        {errorMessage && <div className="text-red-400 text-sm ml-2">{errorMessage}</div>}

        <button
          type="submit"
          className="rounded-lg bg-black px-6 py-3 text-white transition-colors hover:bg-gray-800"
        >
          재설정
        </button>
      </form>
    </section>
  );
}