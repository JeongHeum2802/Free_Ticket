type SellingTicket = {
  id: number;
  title: string;
  price: number;
  status: "판매 중" | "판매 완료";
};

const sellingTickets: SellingTicket[] = [
  {
    id: 1,
    title: "뮤지컬 위키드 R석",
    price: 120000,
    status: "판매 중",
  },
  {
    id: 2,
    title: "아이돌 콘서트 지정석",
    price: 90000,
    status: "판매 중",
  },
];

export default function SellingTickets() {
  return (
    <section>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">내가 판매 중인 티켓</h1>
          <p className="mt-2 text-sm text-gray-500">
            등록한 판매 티켓을 관리할 수 있습니다.
          </p>
        </div>

        <button
          type="button"
          className="rounded-lg bg-black px-4 py-2 text-sm text-white hover:bg-gray-800"
        >
          티켓 판매 등록
        </button>
      </div>

      <div className="mt-6 space-y-4">
        {sellingTickets.length > 0 ? (
          sellingTickets.map((ticket) => (
            <article
              key={ticket.id}
              className="flex items-center justify-between rounded-lg border border-gray-200 p-5"
            >
              <div>
                <h2 className="font-semibold">{ticket.title}</h2>

                <p className="mt-2 text-sm text-gray-600">
                  {ticket.price.toLocaleString()}원
                </p>

                <span className="mt-3 inline-block rounded-full bg-green-100 px-3 py-1 text-xs text-green-700">
                  {ticket.status}
                </span>
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  className="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50"
                >
                  수정
                </button>

                <button
                  type="button"
                  className="rounded-md border border-red-300 px-4 py-2 text-sm text-red-500 hover:bg-red-50"
                >
                  삭제
                </button>
              </div>
            </article>
          ))
        ) : (
          <div className="rounded-lg border border-gray-200 py-20 text-center text-gray-500">
            판매 중인 티켓이 없습니다.
          </div>
        )}
      </div>
    </section>
  );
}