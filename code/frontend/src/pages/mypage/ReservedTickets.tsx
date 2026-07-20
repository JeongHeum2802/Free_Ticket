type Ticket = {
  id: number;
  title: string;
  date: string;
  location: string;
  seat: string;
};

const tickets: Ticket[] = [
  {
    id: 1,
    title: "뮤지컬 팬텀",
    date: "2026년 8월 10일 오후 7시",
    location: "블루스퀘어 신한카드홀",
    seat: "R석 1층 A구역 10열 5번",
  },
  {
    id: 2,
    title: "여름 콘서트",
    date: "2026년 8월 24일 오후 6시",
    location: "고척스카이돔",
    seat: "스탠딩 B구역 120번",
  },
];

export default function ReservedTickets() {
  return (
    <section>
      <h1 className="text-2xl font-bold">내가 예매한 티켓</h1>
      <p className="mt-2 text-sm text-gray-500">
        예매한 공연과 좌석 정보를 확인할 수 있습니다.
      </p>

      <div className="mt-6 space-y-4">
        {tickets.length > 0 ? (
          tickets.map((ticket) => (
            <article
              key={ticket.id}
              className="rounded-lg border border-gray-200 p-5"
            >
              <h2 className="text-lg font-semibold">{ticket.title}</h2>

              <div className="mt-3 space-y-1 text-sm text-gray-600">
                <p>공연 일시: {ticket.date}</p>
                <p>공연 장소: {ticket.location}</p>
                <p>좌석: {ticket.seat}</p>
              </div>

              <button
                type="button"
                className="mt-4 rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50"
              >
                예매 상세 보기
              </button>
            </article>
          ))
        ) : (
          <div className="rounded-lg border border-gray-200 py-20 text-center text-gray-500">
            예매한 티켓이 없습니다.
          </div>
        )}
      </div>
    </section>
  );
}