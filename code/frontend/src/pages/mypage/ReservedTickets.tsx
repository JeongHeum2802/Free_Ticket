import axios from "axios";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";

import { cancelReservation, getMyReservations } from "../../api/orders";
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
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [selected, setSelected] = useState<ReservationHistory | null>(null);
  const [canceling, setCanceling] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [unconfirmedOrderIds, setUnconfirmedOrderIds] = useState<string[]>([]);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const backButtonRef = useRef<HTMLButtonElement>(null);
  const refreshButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!selected || !dialog) return;
    const previousFocus = document.activeElement as HTMLElement | null;
    const refreshButton = refreshButtonRef.current;
    dialog.showModal();
    backButtonRef.current?.focus();

    return () => {
      dialog.close();
      if (previousFocus?.isConnected && !previousFocus.hasAttribute("disabled")) {
        previousFocus.focus();
      } else {
        refreshButton?.focus();
      }
    };
  }, [selected]);

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

  async function refreshReservations() {
    setRefreshing(true);
    setErrorMessage(null);
    setStatusMessage(null);
    try {
      setReservations(await getMyReservations());
      setUnconfirmedOrderIds([]);
    } catch {
      setErrorMessage("예매 내역을 새로고침하지 못했습니다. 다시 시도해 주세요.");
    } finally {
      setRefreshing(false);
    }
  }

  async function confirmCancellation() {
    if (!selected || canceling) return;
    setCanceling(true);
    setErrorMessage(null);
    setStatusMessage(null);

    try {
      const result = await cancelReservation(selected.orderId);
      if (result.status !== "CANCELED") throw new Error("Cancellation not confirmed");
      setReservations(current => current.filter(item => item.orderId !== selected.orderId));
      setStatusMessage(`${selected.eventName} 예매가 취소되었습니다.`);
    } catch (error) {
      const response = axios.isAxiosError<{ message?: string }>(error) ? error.response : undefined;
      const uncertain = !response || response.status >= 500;
      const message = uncertain
        ? "취소 결과를 확인하지 못했습니다. 목록 새로고침으로 처리 상태를 확인해 주세요."
        : response.data?.message ?? "예매를 취소하지 못했습니다.";
      setErrorMessage(message);
      setUnconfirmedOrderIds(current => [...current, selected.orderId]);
      try {
        setReservations(await getMyReservations());
        setUnconfirmedOrderIds(current => current.filter(id => id !== selected.orderId));
      } catch {
        setErrorMessage(`${message} 예매 내역도 새로고침하지 못했습니다.`);
      }
    } finally {
      setSelected(null);
      setCanceling(false);
    }
  }

  return (
    <section>
      <h1 className="text-2xl font-bold">내가 예매한 티켓</h1>
      <p className="mt-2 text-sm text-gray-500">
        결제가 완료된 공연과 티켓 정보를 확인할 수 있습니다.
      </p>
      <button
        ref={refreshButtonRef}
        type="button"
        disabled={loading || refreshing || canceling}
        onClick={refreshReservations}
        className="mt-4 rounded-md border border-gray-300 px-4 py-2 text-sm font-semibold hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {refreshing ? "목록을 새로고침하는 중" : "목록 새로고침"}
      </button>

      {statusMessage && <p role="status" className="mt-4 text-sm text-gray-700">{statusMessage}</p>}

      {loading && (
        <div className="mt-6 rounded-lg border border-gray-200 py-20 text-center text-gray-500">
          예매 내역을 불러오는 중입니다.
        </div>
      )}

      {errorMessage && !loading && (
        <div role="alert" className="mt-6 rounded-lg border border-red-200 bg-red-50 p-4 text-red-600">
          {errorMessage}
        </div>
      )}

      {!loading && (
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
                        {reservation.status === "CANCELING" ? "취소 처리 중" : "결제 완료"}
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

                  {reservation.status === "CANCELING" && (
                    <p className="mt-4 text-sm text-gray-600">
                      취소 처리 중입니다. 처리가 끝난 뒤 목록을 새로고침해 주세요.
                    </p>
                  )}
                  {unconfirmedOrderIds.includes(reservation.orderId) && (
                    <p className="mt-4 text-sm text-gray-600">
                      취소 결과 확인이 필요합니다. 목록을 새로고침해 주세요.
                    </p>
                  )}

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
                    <button
                      type="button"
                      disabled={reservation.status !== "PAID" || canceling || refreshing || unconfirmedOrderIds.includes(reservation.orderId)}
                      onClick={() => setSelected(reservation)}
                      className="rounded-md border border-red-300 px-4 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      예매 취소
                    </button>
                  </div>
                </div>
              </article>
            ))
          ) : (
            <div className="rounded-lg border border-gray-200 py-20 text-center text-gray-500">
              {errorMessage ? "목록 새로고침으로 예매 내역을 다시 확인해 주세요." : "결제가 완료된 예매 내역이 없습니다."}
            </div>
          )}
        </div>
      )}

      {selected && (
        <dialog
          ref={dialogRef}
          aria-labelledby="reservation-cancel-title"
          aria-describedby="reservation-cancel-warning"
          aria-busy={canceling}
          onCancel={event => {
            event.preventDefault();
            if (!canceling) setSelected(null);
          }}
          className="fixed inset-0 m-auto w-[calc(100%_-_2rem)] max-w-lg rounded-xl bg-white p-6 shadow-xl backdrop:bg-black/50"
        >
          <h2 id="reservation-cancel-title" className="text-xl font-bold">예매 취소 확인</h2>
          <dl className="mt-5 grid grid-cols-[76px_1fr] gap-y-3 text-sm">
            <dt className="font-semibold">공연명</dt>
            <dd>{selected.eventName}</dd>
            <dt className="font-semibold">공연 일시</dt>
            <dd>{formatDateTime(selected.performanceAt)}</dd>
            <dt className="font-semibold">수량</dt>
            <dd>{selected.quantity}매</dd>
            <dt className="font-semibold">취소 금액</dt>
            <dd>{selected.amount.toLocaleString()}원</dd>
          </dl>
          <p className="mt-6 font-semibold">이 예매를 정말 취소하시겠습니까?</p>
          <p id="reservation-cancel-warning" className="mt-2 text-sm text-red-700">
            전체 수량을 취소합니다. 취소가 완료되면 되돌릴 수 없습니다.
          </p>
          {canceling && <p className="mt-3 text-sm text-gray-600">취소 요청을 처리하고 있습니다.</p>}
          <div className="mt-6 flex flex-wrap justify-end gap-3">
            <button
              ref={backButtonRef}
              type="button"
              disabled={canceling}
              onClick={() => setSelected(null)}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm font-semibold hover:bg-gray-50 disabled:opacity-50"
            >
              아니요, 돌아가기
            </button>
            <button
              type="button"
              disabled={canceling}
              onClick={confirmCancellation}
              className="rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-800 disabled:opacity-50"
            >
              예, 취소합니다
            </button>
          </div>
        </dialog>
      )}
    </section>
  );
}
