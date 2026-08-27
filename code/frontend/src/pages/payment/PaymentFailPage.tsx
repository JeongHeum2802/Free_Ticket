import { Link, useSearchParams } from "react-router-dom";

export default function PaymentFailPage() {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get("orderId");
  const message = searchParams.get("message") ?? "결제가 취소되었거나 처리 중 오류가 발생했습니다.";

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f8f8f8] px-5">
      <section className="w-full max-w-lg rounded-2xl bg-white p-10 text-center shadow-sm">
        <h1 className="text-3xl font-black">결제를 완료하지 못했습니다.</h1>
        <p className="mt-4 text-gray-600">{message}</p>
        <div className="mt-8 flex justify-center gap-3">
          {orderId && <Link to={`/payment/checkout/${encodeURIComponent(orderId)}`} className="rounded-xl bg-[#453eda] px-6 py-3 font-bold text-white">다시 결제하기</Link>}
          <Link to="/" className="rounded-xl border border-gray-300 px-6 py-3 font-bold">홈으로</Link>
        </div>
      </section>
    </main>
  );
}
