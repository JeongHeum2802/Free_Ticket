import axios from "axios";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { getCheckoutOrder } from "../../api/orders";
import { loadTossPaymentsSdk } from "../../lib/tossPayments";
import type { CheckoutOrder } from "../../types/Payment";

export default function PaymentCheckoutPage() {
  const { orderId = "" } = useParams<{ orderId: string }>();
  const [order, setOrder] = useState<CheckoutOrder | null>(null);
  const [widgets, setWidgets] = useState<TossPaymentWidgets | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadOrder() {
      try {
        const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
        if (!clientKey) {
          throw new Error("VITE_TOSS_CLIENT_KEY가 설정되지 않았습니다.");
        }
        const checkoutOrder = await getCheckoutOrder(orderId);
        await loadTossPaymentsSdk();
        if (active) {
          setWidgets(null);
          setOrder(checkoutOrder);
        }
      } catch (error) {
        if (active) {
          const message = axios.isAxiosError(error)
            ? error.response?.data?.message
            : error instanceof Error ? error.message : null;
          setErrorMessage(message ?? "결제 정보를 불러오지 못했습니다.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadOrder();
    return () => {
      active = false;
    };
  }, [orderId]);

  useEffect(() => {
    if (!order) return;

    let active = true;
    const checkoutOrder = order;

    async function renderWidgets() {
      try {
        const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
        const tossPayments = clientKey ? window.TossPayments?.(clientKey) : null;
        if (!tossPayments) {
          throw new Error("토스페이먼츠 SDK를 초기화하지 못했습니다.");
        }

        const paymentWidgets = tossPayments.widgets({
          customerKey: checkoutOrder.customerKey,
        });
        await paymentWidgets.setAmount({ currency: "KRW", value: checkoutOrder.amount });
        if (!active) return;

        await Promise.all([
          paymentWidgets.renderPaymentMethods({ selector: "#payment-methods", variantKey: "DEFAULT" }),
          paymentWidgets.renderAgreement({ selector: "#payment-agreement", variantKey: "AGREEMENT" }),
        ]);
        if (active) setWidgets(paymentWidgets);
      } catch (error) {
        if (active) {
          setErrorMessage(error instanceof Error ? error.message : "결제위젯을 표시하지 못했습니다.");
        }
      }
    }

    void renderWidgets();
    return () => {
      active = false;
    };
  }, [order]);

  async function handlePayment() {
    if (!order || !widgets) return;
    setPaying(true);
    setErrorMessage(null);
    try {
      const origin = window.location.origin;
      await widgets.requestPayment({
        orderId: order.orderId,
        orderName: order.orderName,
        successUrl: `${origin}/payment/success`,
        failUrl: `${origin}/payment/fail`,
        customerEmail: order.customerEmail,
        customerName: order.customerName,
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "결제창을 열지 못했습니다.");
      setPaying(false);
    }
  }

  if (errorMessage && !order) {
    return <PaymentShell><ResultMessage title="결제를 준비하지 못했습니다." message={errorMessage} /></PaymentShell>;
  }

  return (
    <PaymentShell>
      <h1 className="text-3xl font-black text-gray-900">결제하기</h1>
      {loading && <p className="mt-8 text-center text-gray-500">결제 정보를 준비하고 있습니다.</p>}
      {order && (
        <>
          <section className="mt-8 rounded-2xl bg-gray-50 p-6">
            <p className="text-sm text-gray-500">주문 상품</p>
            <h2 className="mt-2 text-lg font-bold text-gray-900">{order.orderName}</h2>
            <div className="mt-5 flex items-end justify-between border-t border-gray-200 pt-5">
              <span className="text-sm text-gray-600">{order.quantity}매</span>
              <strong className="text-2xl text-[#453eda]">{order.amount.toLocaleString()}원</strong>
            </div>
          </section>
          <div id="payment-methods" className="mt-6" />
          <div id="payment-agreement" />
          {errorMessage && <p className="mb-4 text-center text-sm font-bold text-red-500">{errorMessage}</p>}
          <button
            type="button"
            disabled={!widgets || paying}
            onClick={() => void handlePayment()}
            className="w-full rounded-xl bg-[#453eda] px-6 py-4 text-lg font-bold text-white hover:bg-[#352fc0] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {paying ? "결제창 여는 중..." : `${order.amount.toLocaleString()}원 결제하기`}
          </button>
        </>
      )}
    </PaymentShell>
  );
}

function PaymentShell({ children }: { children: React.ReactNode }) {
  return <main className="min-h-screen bg-[#f8f8f8] px-5 py-12"><div className="mx-auto max-w-2xl rounded-2xl bg-white p-6 shadow-sm md:p-10">{children}</div></main>;
}

function ResultMessage({ title, message }: { title: string; message: string }) {
  return <div className="py-14 text-center"><h1 className="text-2xl font-black">{title}</h1><p className="mt-4 text-gray-600">{message}</p><Link to="/" className="mt-8 inline-block rounded-xl bg-[#453eda] px-6 py-3 font-bold text-white">홈으로</Link></div>;
}
