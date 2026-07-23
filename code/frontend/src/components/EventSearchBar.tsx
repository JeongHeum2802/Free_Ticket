import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

type SearchPanel = "category" | "location" | "date" | "people" | null;

const categoryByPath: Record<string, string> = {
  "/concert": "concert",
  "/musical": "musical",
  "/play": "play",
  "/classic": "classic",
  "/exhibition": "exhibition",
  "/busking": "busking",
};

const categoryOptions = [
  { value: "all", label: "전체", icon: "✨" },
  { value: "concert", label: "콘서트", icon: "🎤" },
  { value: "musical", label: "뮤지컬", icon: "🎼" },
  { value: "play", label: "연극", icon: "🎭" },
  { value: "classic", label: "클래식/무용", icon: "🎻" },
  { value: "exhibition", label: "전시/스포츠", icon: "🖼️" },
  { value: "busking", label: "버스킹", icon: "🎸" },
];

const recommendedLocations = [
  {
    name: "서울",
    description: "다양한 공연과 전시를 한눈에",
    icon: "🏙️",
  },
  {
    name: "경기",
    description: "가까운 지역의 새로운 무대",
    icon: "🎭",
  },
  {
    name: "부산",
    description: "바다와 함께 즐기는 문화생활",
    icon: "🌊",
  },
  {
    name: "제주",
    description: "여행 중 만나는 특별한 공연",
    icon: "🌴",
  },
];

function parsePeople(value: string | null) {
  const parsedPeople = Number(value);

  if (!Number.isFinite(parsedPeople) || parsedPeople < 1) {
    return 1;
  }

  return Math.floor(parsedPeople);
}

export default function EventSearchBar() {
  const currentLocation = useLocation();
  const navigate = useNavigate();
  const currentParams = new URLSearchParams(currentLocation.search);
  const normalizedPath =
    currentLocation.pathname.replace(/\/+$/, "") || "/";

  const [activePanel, setActivePanel] = useState<SearchPanel>(null);
  const [category, setCategory] = useState(() => {
    const initialCategory =
      normalizedPath === "/search"
        ? currentParams.get("category") ?? "all"
        : categoryByPath[normalizedPath] ?? "all";

    return categoryOptions.some(
      (categoryOption) => categoryOption.value === initialCategory,
    )
      ? initialCategory
      : "all";
  });
  const [region, setRegion] = useState(
    normalizedPath === "/search"
      ? currentParams.get("region") ?? ""
      : "",
  );
  const [startDate, setStartDate] = useState(
    normalizedPath === "/search"
      ? currentParams.get("startDate") ?? ""
      : "",
  );
  const [endDate, setEndDate] = useState(
    normalizedPath === "/search"
      ? currentParams.get("endDate") ?? ""
      : "",
  );
  const [people, setPeople] = useState(
    normalizedPath === "/search"
      ? parsePeople(currentParams.get("people"))
      : 1,
  );

  if (normalizedPath === "/mypage" || normalizedPath.startsWith("/mypage/")) {
    return null;
  }

  const dateSummary =
    startDate && endDate
      ? `${startDate} ~ ${endDate}`
      : startDate
        ? `${startDate}부터`
        : endDate
          ? `${endDate}까지`
          : "날짜 추가";

  const selectedCategoryLabel =
    categoryOptions.find(
      (categoryOption) => categoryOption.value === category,
    )?.label ?? "전체";

  const handleStartDateChange = (nextStartDate: string) => {
    setStartDate(nextStartDate);

    if (endDate && nextStartDate > endDate) {
      setEndDate("");
    }
  };

  const handleSearch = () => {
    const searchParams = new URLSearchParams({
      category,
      people: String(people),
    });

    if (region) {
      searchParams.set("region", region);
    }

    if (startDate) {
      searchParams.set("startDate", startDate);
    }

    if (endDate) {
      searchParams.set("endDate", endDate);
    }

    setActivePanel(null);
    navigate(`/search?${searchParams.toString()}`);
  };

  return (
    <section className="relative z-30 border-b border-gray-200 bg-white px-6 py-5">
      <div className="relative mx-auto max-w-6xl">
        <div className="grid overflow-hidden rounded-[32px] border border-gray-200 bg-white shadow-[0_8px_30px_rgba(0,0,0,0.1)] md:grid-cols-[1fr_1.15fr_1fr_0.8fr_auto]">
          <button
            type="button"
            onClick={() =>
              setActivePanel(activePanel === "category" ? null : "category")
            }
            className={`px-7 py-4 text-left transition-colors hover:bg-gray-50 ${
              activePanel === "category" ? "bg-gray-100" : ""
            }`}
          >
            <span className="block text-xs font-bold">장르</span>
            <span className="mt-1 block truncate text-sm text-gray-500">
              {selectedCategoryLabel}
            </span>
          </button>

          <button
            type="button"
            onClick={() =>
              setActivePanel(activePanel === "location" ? null : "location")
            }
            className={`px-7 py-4 text-left transition-colors hover:bg-gray-50 ${
              activePanel === "location" ? "bg-gray-100" : ""
            }`}
          >
            <span className="block text-xs font-bold">지역</span>
            <span className="mt-1 block truncate text-sm text-gray-500">
              {region || "지역 검색"}
            </span>
          </button>

          <button
            type="button"
            onClick={() =>
              setActivePanel(activePanel === "date" ? null : "date")
            }
            className={`border-t border-gray-200 px-7 py-4 text-left transition-colors hover:bg-gray-50 md:border-l md:border-t-0 ${
              activePanel === "date" ? "bg-gray-100" : ""
            }`}
          >
            <span className="block text-xs font-bold">날짜</span>
            <span className="mt-1 block truncate text-sm text-gray-500">
              {dateSummary}
            </span>
          </button>

          <button
            type="button"
            onClick={() =>
              setActivePanel(activePanel === "people" ? null : "people")
            }
            className={`border-t border-gray-200 px-7 py-4 text-left transition-colors hover:bg-gray-50 md:border-l md:border-t-0 ${
              activePanel === "people" ? "bg-gray-100" : ""
            }`}
          >
            <span className="block text-xs font-bold">인원</span>
            <span className="mt-1 block text-sm text-gray-500">
              {people}명
            </span>
          </button>

          <div className="flex items-center justify-center border-t border-gray-200 p-3 md:border-l md:border-t-0">
            <button
              type="button"
              onClick={handleSearch}
              className="flex w-full items-center justify-center gap-2 rounded-full bg-[#453eda] px-7 py-3 font-bold text-white transition-colors hover:bg-[#352fc0] md:w-auto"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={2}
                stroke="currentColor"
                className="h-5 w-5"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="m21 21-4.35-4.35m0 0A7.5 7.5 0 1 0 6.04 6.04a7.5 7.5 0 0 0 10.61 10.61Z"
                />
              </svg>
              검색
            </button>
          </div>
        </div>

        {activePanel === "category" && (
          <div className="absolute left-0 top-[calc(100%+12px)] z-40 w-full rounded-3xl border border-gray-100 bg-white p-6 shadow-[0_18px_50px_rgba(0,0,0,0.16)] md:max-w-md">
            <p className="text-sm font-bold">장르 선택</p>
            <div className="mt-4 grid grid-cols-2 gap-2">
              {categoryOptions.map((categoryOption) => (
                <button
                  key={categoryOption.value}
                  type="button"
                  onClick={() => {
                    setCategory(categoryOption.value);
                    setActivePanel(null);
                  }}
                  className={`flex items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-colors ${
                    category === categoryOption.value
                      ? "border-[#453eda] bg-[#f3f1ff] text-[#453eda]"
                      : "border-gray-200 hover:bg-gray-50"
                  }`}
                >
                  <span className="text-xl">{categoryOption.icon}</span>
                  <span className="text-sm font-bold">
                    {categoryOption.label}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}

        {activePanel === "location" && (
          <div className="absolute left-0 top-[calc(100%+12px)] z-40 w-full rounded-3xl border border-gray-100 bg-white p-6 shadow-[0_18px_50px_rgba(0,0,0,0.16)] md:max-w-md">
            <p className="text-sm font-bold">추천 지역</p>
            <div className="mt-4 space-y-2">
              <button
                type="button"
                onClick={() => {
                  setRegion("");
                  setActivePanel(null);
                }}
                className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-left text-sm font-bold transition-colors hover:bg-gray-50"
              >
                전체 지역
              </button>
              {recommendedLocations.map((item) => (
                <button
                  key={item.name}
                  type="button"
                  onClick={() => {
                    setRegion(item.name);
                    setActivePanel(null);
                  }}
                  className="flex w-full items-center gap-4 rounded-2xl p-3 text-left transition-colors hover:bg-gray-50"
                >
                  <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-[#f3f1ff] text-2xl">
                    {item.icon}
                  </span>
                  <span>
                    <span className="block font-bold">{item.name}</span>
                    <span className="mt-0.5 block text-sm text-gray-500">
                      {item.description}
                    </span>
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}

        {activePanel === "date" && (
          <div className="absolute left-0 top-[calc(100%+12px)] z-40 w-full rounded-3xl border border-gray-100 bg-white p-7 shadow-[0_18px_50px_rgba(0,0,0,0.16)] md:left-1/2 md:max-w-xl md:-translate-x-1/2">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-lg font-bold">관람 날짜</p>
                <p className="mt-1 text-sm text-gray-500">
                  원하는 기간을 선택해 주세요.
                </p>
              </div>
              <span className="rounded-full bg-[#f3f1ff] px-4 py-2 text-sm font-bold text-[#453eda]">
                날짜 지정
              </span>
            </div>

            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              <label className="rounded-2xl border border-gray-200 p-4 focus-within:border-[#453eda]">
                <span className="block text-xs font-bold">시작일</span>
                <input
                  type="date"
                  value={startDate}
                  onChange={(event) =>
                    handleStartDateChange(event.target.value)
                  }
                  className="mt-2 w-full bg-transparent text-sm outline-none"
                />
              </label>
              <label className="rounded-2xl border border-gray-200 p-4 focus-within:border-[#453eda]">
                <span className="block text-xs font-bold">종료일</span>
                <input
                  type="date"
                  value={endDate}
                  min={startDate}
                  disabled={!startDate}
                  onChange={(event) => setEndDate(event.target.value)}
                  className="mt-2 w-full bg-transparent text-sm outline-none disabled:cursor-not-allowed disabled:opacity-40"
                />
              </label>
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => {
                  setStartDate("");
                  setEndDate("");
                }}
                className="rounded-xl border border-gray-300 py-3 text-sm font-bold hover:bg-gray-50"
              >
                날짜 초기화
              </button>
              <button
                type="button"
                onClick={() => setActivePanel(null)}
                className="rounded-xl bg-black py-3 text-sm font-bold text-white hover:bg-gray-800"
              >
                날짜 선택 완료
              </button>
            </div>
          </div>
        )}

        {activePanel === "people" && (
          <div className="absolute right-0 top-[calc(100%+12px)] z-40 w-full rounded-3xl border border-gray-100 bg-white p-7 shadow-[0_18px_50px_rgba(0,0,0,0.16)] md:max-w-sm">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-bold">관람 인원</p>
                <p className="mt-1 text-sm text-gray-500">
                  함께 관람할 인원이에요.
                </p>
              </div>
              <div className="flex items-center gap-4">
                <button
                  type="button"
                  aria-label="인원 줄이기"
                  disabled={people === 1}
                  onClick={() =>
                    setPeople((currentPeople) =>
                      Math.max(1, currentPeople - 1),
                    )
                  }
                  className="flex h-9 w-9 items-center justify-center rounded-full border border-gray-300 text-xl disabled:cursor-not-allowed disabled:opacity-30"
                >
                  −
                </button>
                <span className="w-5 text-center font-bold">{people}</span>
                <button
                  type="button"
                  aria-label="인원 늘리기"
                  onClick={() =>
                    setPeople((currentPeople) => currentPeople + 1)
                  }
                  className="flex h-9 w-9 items-center justify-center rounded-full border border-gray-300 text-xl hover:border-black"
                >
                  +
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
