import { useParams } from "react-router-dom";

import type { Ticket } from "../types/Ticket";

const tickets: Ticket[] = [
  {
    id: 1,
    title: "뮤지컬 프리다",
    category: "뮤지컬",
    posterUrl:
      "https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_s_58949.jpg/dims/quality/",
    place: "블루스퀘어 신한카드홀",
    date: "2026.07.20 ~ 2026.10.10",
    runningTime: "160분",
    ageRating: "14세 이상 관람가",
    price: 140000,
  },
  {
    id: 2,
    title: "2026 여름 콘서트",
    category: "콘서트",
    posterUrl:
      "https://tkfile.yes24.com/upload2/perfblog/202606/20260618/20260618-58962.jpg/dims/quality/70/",
    place: "KSPO DOME",
    date: "2026.08.15 ~ 2026.08.16",
    runningTime: "120분",
    ageRating: "전체 관람가",
    price: 132000,
  },
];

export default function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();

  const ticket = tickets.find(
    (ticket) => ticket.id === Number(id)
  );

  if (!ticket) {
    return (
      <main className="flex min-h-[500px] items-center justify-center">
        <p className="text-xl font-bold text-gray-500">
          공연 정보를 찾을 수 없습니다.
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f8f8f8] px-6 py-14">
      <div className="mx-auto max-w-6xl">
        <div className="rounded-2xl bg-white p-10 shadow-sm">
          <div className="grid gap-12 md:grid-cols-[340px_1fr]">
            {/* 왼쪽 포스터 */}
            <div>
              <img
                src={ticket.posterUrl}
                alt={`${ticket.title} 포스터`}
                className="w-full rounded-xl object-cover"
              />
            </div>

            {/* 오른쪽 공연 정보 */}
            <section>
              <span className="text-sm font-bold text-[#453eda]">
                {ticket.category}
              </span>

              <h1 className="mt-3 text-4xl font-black text-gray-900">
                {ticket.title}
              </h1>

              <div className="mt-8 border-y border-gray-200 py-6">
                <dl className="grid grid-cols-[100px_1fr] gap-y-5 text-sm">
                  <dt className="font-bold text-gray-700">공연 장소</dt>
                  <dd className="text-gray-600">{ticket.place}</dd>

                  <dt className="font-bold text-gray-700">공연 기간</dt>
                  <dd className="text-gray-600">{ticket.date}</dd>

                  <dt className="font-bold text-gray-700">관람 시간</dt>
                  <dd className="text-gray-600">{ticket.runningTime}</dd>

                  <dt className="font-bold text-gray-700">관람 등급</dt>
                  <dd className="text-gray-600">{ticket.ageRating}</dd>
                </dl>
              </div>

              <div className="mt-8 flex items-end justify-between">
                <div>
                  <p className="text-sm text-gray-500">티켓 가격</p>

                  <p className="mt-1 text-3xl font-black text-gray-900">
                    {ticket.price.toLocaleString()}원
                  </p>
                </div>

                <button
                  type="button"
                  className="rounded-xl bg-[#453eda] px-10 py-4 font-bold text-white transition-colors hover:bg-[#352fc0]"
                >
                  예매하기
                </button>
              </div>
            </section>
          </div>
        </div>
      </div>
    </main>
  );
}