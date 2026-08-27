import axios from "axios";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { getMyReservations } from "../../api/orders";
import type { ReservationHistory } from "../../types/Payment";

function formatDateTime(value: string | null) {
  if (!value) return "일정 미정";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatPaidAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

export default function ReservedTickets() {
  const [reservations, setReservations] = useState<ReservationHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    getMyReservations()
      .then((data) => {
        if (active) setReservations(data);
      })
      .catch((error) => {
        if (!active) return;
        const message = axios.isAxiosError(error)
          ? error.response?.data?.message
          : null;
        setErrorMessage(message ?? "예매 내역을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <section>
      <h1 className="text-2xl font-bold">내가 예매한 티켓</h1>
      <p className="mt-2 text-sm text-gray-500">
        결제가 완료된 공연과 티켓 정보를 확인할 수 있습니다.
      </p>

      {loading && (
        <div className="mt-6 rounded-lg border border-gray-200 py-20 text-center text-gray-500">
          예매 내역을 불러오는 중입니다.
        </div>
      )}

      {errorMessage && !loading && (
        <div className="mt-6 rounded-lg border border-red-200 bg-red-50 py-16 text-center text-red-600">
          {errorMessage}
        </div>
      )}

      {!loading && !errorMessage && (
        <div className="mt-6 space-y-4">
          {reservations.length > 0 ? (
            reservations.map((reservation) => (
              <article
                key={reservation.orderId}
                className="overflow-hidden rounded-xl border border-gray-200 sm:flex"
              >
                <img
                  src={reservation.mainImageUrl}
                  alt={`${reservation.eventName} 포스터`}
                  className="h-52 w-full object-cover sm:h-auto sm:w-36"
                />
                <div className="min-w-0 flex-1 p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <span className="rounded-full bg-[#f3f1ff] px-3 py-1 text-xs font-bold text-[#453eda]">
                        결제 완료
                      </span>
                      <h2 className="mt-3 text-lg font-semibold">{reservation.eventName}</h2>
                    </div>
                    <strong className="text-lg">{reservation.amount.toLocaleString()}원</strong>
                  </div>

                  <dl className="mt-4 grid grid-cols-[76px_1fr] gap-y-2 text-sm text-gray-600">
                    <dt className="font-semibold text-gray-800">공연 일시</dt>
                    <dd>{formatDateTime(reservation.performanceAt)}</dd>
                    <dt className="font-semibold text-gray-800">공연 장소</dt>
                    <dd>{reservation.location}</dd>
                    <dt className="font-semibold text-gray-800">티켓</dt>
                    <dd>{reservation.ticketType} · {reservation.quantity}매</dd>
                    <dt className="font-semibold text-gray-800">결제 정보</dt>
                    <dd>{formatPaidAt(reservation.paidAt)} · {reservation.paymentMethod ?? "결제수단 확인 불가"}</dd>
                    <dt className="font-semibold text-gray-800">주문번호</dt>
                    <dd className="break-all">{reservation.orderId}</dd>
                  </dl>

                  <div className="mt-5 flex flex-wrap gap-2">
                    <Link
                      to={`/ticket/${reservation.eventId}`}
                      className="rounded-md border border-gray-300 px-4 py-2 text-sm font-semibold hover:bg-gray-50"
                    >
                      공연 상세 보기
                    </Link>
                    {reservation.receiptUrl && (
                      <a
                        href={reservation.receiptUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="rounded-md bg-[#453eda] px-4 py-2 text-sm font-semibold text-white hover:bg-[#352fc0]"
                      >
                        영수증 보기
                      </a>
                    )}
                  </div>
                </div>
              </article>
            ))
          ) : (
            <div className="rounded-lg border border-gray-200 py-20 text-center text-gray-500">
              결제가 완료된 예매 내역이 없습니다.
            </div>
          )}
        </div>
      )}
    </section>
  );
}
