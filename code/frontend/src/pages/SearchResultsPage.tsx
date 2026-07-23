import { useSearchParams } from "react-router-dom";

type SearchEvent = {
  eventid: number;
  name: string;
  category: string;
  startTime: string;
  endTime: string;
  location: string;
  postUrl: string;
};

const categoryLabels: Record<string, string> = {
  all: "전체",
  busking: "버스킹",
  classic: "클래식/무용",
  concert: "콘서트",
  exhibition: "전시/스포츠",
  musical: "뮤지컬",
  play: "연극",
};

const searchEvents: SearchEvent[] = [
  {
    eventid: 601,
    name: "한강 노을 버스킹",
    category: "busking",
    startTime: "2026-08-03T18:30:00",
    endTime: "2026-08-03T20:00:00",
    location: "서울특별시 영등포구 여의동로 330",
    postUrl:
      "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615566/event-601-post_wfisid.png",
  },
  {
    eventid: 502,
    name: "피아노와 함께하는 여행",
    category: "classic",
    startTime: "2026-08-27T19:30:00",
    endTime: "2026-08-27T21:30:00",
    location: "경기도 수원시 팔달구 효원로307번길 20",
    postUrl:
      "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615562/event-501-post_hqkcog.png",
  },
  {
    eventid: 401,
    name: "여름밤의 멜로디",
    category: "concert",
    startTime: "2026-08-08T18:00:00",
    endTime: "2026-08-08T21:00:00",
    location: "서울특별시 송파구 올림픽로 424",
    postUrl:
      "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615559/event-401-post_mm8z7x.png",
  },
  {
    eventid: 302,
    name: "도시의 숨결",
    category: "exhibition",
    startTime: "2026-08-15T10:30:00",
    endTime: "2026-09-20T19:00:00",
    location: "서울특별시 중구 을지로 281",
    postUrl:
      "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615557/event-301-post_qbyrgf.png",
  },
  {
    eventid: 101,
    name: "별빛 아래 우리",
    category: "musical",
    startTime: "2026-08-05T19:30:00",
    endTime: "2026-08-05T22:00:00",
    location: "서울특별시 종로구 대학로 12길 21",
    postUrl:
      "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615553/event-101-post_vwma6m.png",
  },
  {
    eventid: 202,
    name: "우리 집에 낯선 사람이 산다",
    category: "play",
    startTime: "2026-08-22T15:00:00",
    endTime: "2026-08-22T17:00:00",
    location: "경기도 성남시 분당구 성남대로 808",
    postUrl:
      "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615554/event-201-post_ojcvcu.png",
  },
];

function formatDate(dateValue: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(new Date(dateValue));
}

function parsePeople(value: string | null) {
  const parsedPeople = Number(value);

  if (!Number.isFinite(parsedPeople) || parsedPeople < 1) {
    return 1;
  }

  return Math.floor(parsedPeople);
}

export default function SearchResultsPage() {
  const [searchParams] = useSearchParams();
  const category = searchParams.get("category") ?? "all";
  const region = searchParams.get("region") ?? "";
  const startDate = searchParams.get("startDate") ?? "";
  const endDate = searchParams.get("endDate") ?? "";
  const people = parsePeople(searchParams.get("people"));

  const filteredEvents = searchEvents.filter((event) => {
    const matchesCategory =
      category === "all" || event.category === category;
    const matchesRegion = !region || event.location.includes(region);
    const eventStartDate = event.startTime.slice(0, 10);
    const eventEndDate = event.endTime.slice(0, 10);
    const matchesStartDate = !startDate || eventEndDate >= startDate;
    const matchesEndDate = !endDate || eventStartDate <= endDate;

    return (
      matchesCategory &&
      matchesRegion &&
      matchesStartDate &&
      matchesEndDate
    );
  });

  const dateSummary =
    startDate && endDate
      ? `${startDate} ~ ${endDate}`
      : startDate
        ? `${startDate}부터`
        : endDate
          ? `${endDate}까지`
          : "전체 날짜";

  return (
    <main className="min-h-screen bg-[#f7f7f7] px-6 py-14 text-[#222222]">
      <section className="mx-auto max-w-7xl">
        <p className="text-sm font-bold text-[#453eda]">
          {categoryLabels[category] ?? "전체"} 검색 결과
        </p>
        <div className="mt-2 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <h1 className="text-4xl font-black">공연을 찾아봤어요</h1>
            <p className="mt-3 text-gray-500">
              {region || "모든 지역"} · {dateSummary} · {people}명
            </p>
          </div>
          <p className="text-sm font-bold text-gray-500">
            총 {filteredEvents.length}개의 이벤트
          </p>
        </div>

        {filteredEvents.length > 0 ? (
          <div className="mt-10 grid gap-x-5 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
            {filteredEvents.map((event) => (
              <article key={event.eventid} className="group">
                <div className="aspect-[4/5] overflow-hidden rounded-2xl bg-gray-200">
                  <img
                    src={event.postUrl}
                    alt={`${event.name} 포스터`}
                    className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                  />
                </div>
                <div className="mt-4">
                  <span className="text-xs font-bold text-[#453eda]">
                    {categoryLabels[event.category]}
                  </span>
                  <h2 className="mt-1 text-lg font-bold">{event.name}</h2>
                  <p className="mt-2 truncate text-sm text-gray-500">
                    {event.location}
                  </p>
                  <p className="mt-1 text-sm text-gray-700">
                    {formatDate(event.startTime)} ~{" "}
                    {formatDate(event.endTime)}
                  </p>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="mt-10 rounded-3xl border border-dashed border-gray-300 bg-white py-24 text-center">
            <p className="text-xl font-bold">검색 결과가 없습니다.</p>
            <p className="mt-2 text-sm text-gray-500">
              지역이나 날짜 조건을 바꿔 다시 검색해 주세요.
            </p>
          </div>
        )}
      </section>
    </main>
  );
}
