import { useEffect, useState, type FormEvent } from "react";
import { useLocation, useSearchParams, type SetURLSearchParams } from "react-router-dom";
import { getEvents } from "../api/events";
import { eventCategoryLabels, type EventCategory, type EventSummary } from "../types/Event";
import PosterSection from "./eventpage/PosterSection";
import { searchCities } from "./searchCities";

const fieldClass = "mt-2 h-12 w-full rounded border border-[#d4d4d4] bg-white px-3 text-base text-[#222] focus:border-[#453eda] focus:outline-2 focus:outline-[#453eda]";

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  return <SearchContent key={location.key} params={params} setParams={setParams} />;
}

function SearchContent({ params, setParams }: { params: URLSearchParams; setParams: SetURLSearchParams }) {
  const title = params.get("title") ?? "";
  const region = params.get("region") ?? "";
  const category = params.get("category") ?? "";
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    getEvents(category ? category as EventCategory : undefined, {
      ...(title ? { title } : {}), ...(region ? { region } : {}),
    }).then((result) => {
      if (active) setEvents(result);
    }).catch(() => {
      if (active) setError(true);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [title, region, category]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = new URLSearchParams();
    new FormData(event.currentTarget).forEach((value, name) => {
      const text = String(value).trim();
      if (text) next.set(name, text);
    });
    setParams(next);
  }

  return (
    <main className="min-h-screen bg-white pb-20 text-[#222]">
      <section className="mx-auto max-w-[1100px] px-6 pb-12 pt-12 sm:pt-16">
        <h1 className="text-3xl font-extrabold sm:text-4xl">공연 검색</h1>
        <p className="mt-3 text-[#666]">보고 싶은 공연을 제목, 지역, 카테고리로 찾아보세요.</p>
        <form role="search" onSubmit={search} className="mt-8 border-y border-[#ddd] py-6">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr]">
            <div className="text-sm font-semibold sm:col-span-2 lg:col-span-1">
              <label htmlFor="search-title">공연 제목</label>
              <input id="search-title" name="title" type="search" defaultValue={title} maxLength={100} placeholder="공연 제목을 입력하세요" className={fieldClass} />
            </div>
            <div className="text-sm font-semibold">
              <label htmlFor="search-region">지역</label>
              <select id="search-region" name="region" defaultValue={region} className={fieldClass}>
                <option value="">전국</option>
                {region && !Object.values(searchCities).some((cities) => cities.includes(region)) && <option value={region}>{region}</option>}
                {Object.entries(searchCities).map(([province, cities]) => (
                  <optgroup key={province} label={province}>
                    {cities.map((city) => <option key={city} value={city}>{city === "광주시" ? "광주시 (경기)" : city}</option>)}
                  </optgroup>
                ))}
              </select>
            </div>
            <div className="text-sm font-semibold">
              <label htmlFor="search-category">카테고리</label>
              <select id="search-category" name="category" defaultValue={category} className={fieldClass}>
                <option value="">전체</option>
                {category && !Object.hasOwn(eventCategoryLabels, category) && <option value={category}>{category}</option>}
                {Object.entries(eventCategoryLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </div>
          </div>
          <div className="mt-5 flex justify-end gap-3">
            <button type="button" onClick={() => setParams({})} className="min-h-11 rounded px-5 text-sm text-[#666] hover:bg-[#f6f6f6] focus-visible:outline-2 focus-visible:outline-[#453eda]">초기화</button>
            <button type="submit" className="min-h-11 rounded bg-[#453eda] px-8 text-sm font-semibold text-white hover:bg-[#3730b0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#453eda]">검색</button>
          </div>
        </form>
      </section>
      <div aria-live="polite" aria-busy={loading}>
        {loading && <p role="status" className="px-6 py-16 text-center text-[#666]">공연을 검색하고 있습니다.</p>}
        {!loading && error && <p role="alert" className="px-6 py-16 text-center text-red-700">공연을 불러오지 못했습니다. 검색 버튼을 눌러 다시 시도해 주세요.</p>}
        {!loading && !error && (
          <>
            <PosterSection title={`검색 결과 ${events.length}건`} events={events} showMore={false} />
            {events.length === 0 && <div className="px-6 py-16 text-center">
              <p className="font-medium">검색 조건에 맞는 공연이 없습니다.</p>
              <p className="mt-2 text-sm text-[#666]">다른 제목을 입력하거나 지역과 카테고리를 변경해 보세요.</p>
            </div>}
          </>
        )}
      </div>
    </main>
  );
}
