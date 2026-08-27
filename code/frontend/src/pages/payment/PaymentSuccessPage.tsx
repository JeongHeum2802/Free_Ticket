import axios from "axios";
import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { confirmPayment } from "../../api/payments";
import type { PaymentResult } from "../../types/Payment";

export default function PaymentSuccessPage() {
  const [searchParams] = useSearchParams();
  const [result, setResult] = useState<PaymentResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const requested = useRef(false);
  const paymentKey = searchParams.get("paymentKey");
  const orderId = searchParams.get("orderId");
  const amount = Number(searchParams.get("amount"));
  const invalidRequest = !paymentKey || !orderId || !Number.isSafeInteger(amount) || amount <= 0;
  const visibleError = invalidRequest ? "결제 승인 정보가 올바르지 않습니다." : errorMessage;

  useEffect(() => {
    if (requested.current || invalidRequest) return;
    requested.current = true;

    confirmPayment({ paymentKey, orderId, amount })
      .then(setResult)
      .catch((error) => {
        const message = axios.isAxiosError(error) ? error.response?.data?.message : null;
        setErrorMessage(message ?? "결제 승인에 실패했습니다.");
      });
  }, [amount, invalidRequest, orderId, paymentKey]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f8f8f8] px-5">
      <section className="w-full max-w-lg rounded-2xl bg-white p-10 text-center shadow-sm">
        {!result && !visibleError && <><h1 className="text-2xl font-black">결제를 승인하고 있습니다.</h1><p className="mt-4 text-gray-500">창을 닫지 말고 잠시만 기다려 주세요.</p></>}
        {result && <><div className="text-5xl">✓</div><h1 className="mt-5 text-3xl font-black">결제가 완료되었습니다.</h1><p className="mt-4 text-gray-600">{result.amount.toLocaleString()}원 · {result.method}</p>{result.receiptUrl && <a href={result.receiptUrl} target="_blank" rel="noreferrer" className="mt-4 inline-block text-sm font-bold text-[#453eda] underline">영수증 보기</a>}<div><Link to="/mypage/tickets" className="mt-8 inline-block rounded-xl bg-[#453eda] px-7 py-3 font-bold text-white">예매 내역 보기</Link></div></>}
        {visibleError && <><h1 className="text-2xl font-black text-red-500">결제 승인을 완료하지 못했습니다.</h1><p className="mt-4 text-gray-600">{visibleError}</p><Link to="/" className="mt-8 inline-block rounded-xl border border-gray-300 px-7 py-3 font-bold">홈으로</Link></>}
      </section>
    </main>
  );
}
