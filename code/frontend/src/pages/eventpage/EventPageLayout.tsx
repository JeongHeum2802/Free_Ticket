import SlidePosts1 from "../../components/SlidePosts1";

import PosterSection from "./PosterSection";
import WeeklyRankingSection from "./WeeklyRankingSection";

import type { EventPageItem } from "./EventPageTypes";

type EventPageLayoutProps = {
  categoryName: string;
  events: EventPageItem[];
  weeklyRankingEvents?: EventPageItem[];
};

function getEventEndTime(event: EventPageItem): number {
  const date =
    event.end_time instanceof Date
      ? event.end_time
      : new Date(event.end_time);
    
  if (Number.isNaN(date.getTime())) {
    return Number.MAX_SAFE_INTEGER;
  }

  return date.getTime();
}

export default function EventPageLayout({
  categoryName,
  events,
  weeklyRankingEvents,
}: EventPageLayoutProps) {
  const closingSoonEvents = [...events].sort(
    (firstEvent, secondEvent) =>
      getEventEndTime(firstEvent) -
      getEventEndTime(secondEvent)
  );
  
  const rankingEvents = weeklyRankingEvents ?? events.slice(0,5);


  const heroSlides = events
    .filter(
      (event) =>
        event.imageUrl.trim() !== "" &&
        event.postUrl.trim() !== ""
    )
    .slice(0, 3)
    .map((event) => ({
      id: event.eventid,
      name: event.name,
      imageUrl: event.imageUrl,
      posterUrl: event.postUrl,
    }));

  return (
    <main className="min-h-screen bg-white text-[#222222]">
      {
        
      }
      {/* <SlidePosts /> */}
      <SlidePosts1
        key={`${categoryName}-${heroSlides
          .map((slide) => slide.id)
          .join("-")}`}
        slides={heroSlides}
      />

      {/* 카테고리 페이지 제목 */}
      <section className="py-20 text-center">

        <h1 className="text-4xl font-extrabold text-[#222222]">
          {categoryName}
        </h1>

        <div className="mx-auto mt-5 h-[3px] w-10 bg-[#f36f21]" />
      </section>

      {events.length > 0 ? (
        <>
          <section className="mb-24">
            <PosterSection
              title="WHAT'S HOT"
              events={events.slice(0, 5)}
            />
          </section>

          {/* WEEKLY RANKING */}
          <section className="mb-24 bg-[#f7f7f7] py-20">
            <WeeklyRankingSection
              events={rankingEvents}
            />
          </section>

          <section className="mb-24 bg-[#f7f7f7] py-20">
            <PosterSection
              title="마감 임박!"
              events={closingSoonEvents.slice(0, 5)}
              showMore={false}
            />
          </section>

          <section className="pb-24">
            <PosterSection
              title="FREE TICKET'S PICKS"
              events={events}
            />
          </section>
        </>
      ) : (
        <p className="py-32 text-center text-gray-500">
          등록된 이벤트가 없습니다.
        </p>
      )}
    </main>
  );
}