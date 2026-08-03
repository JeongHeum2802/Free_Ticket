import { useEffect, useState } from "react";
import axios from "axios";

import { getEvents, getWeeklyRanking, getWhatsHot, hasBannerImage } from "../../api/events";
import SlidePosts from "../../components/SlidePosts1";
import type { EventCategory, EventSummary, RankedEvent } from "../../types/Event";
import PosterSection from "./PosterSection";
import WeeklyRankingSection from "./WeeklyRankingSection";

type EventPageLayoutProps = {
  category: EventCategory;
  categoryName: string;
};

export default function EventPageLayout({
  category,
  categoryName,
}: EventPageLayoutProps) {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [hotEvents, setHotEvents] = useState<RankedEvent[]>([]);
  const [rankingEvents, setRankingEvents] = useState<RankedEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    const loadPage = async () => {
      setLoading(true);
      setErrorMessage(null);

      try {
        const [eventList, whatsHot, weeklyRanking] = await Promise.all([
          getEvents(category),
          getWhatsHot({ category, limit: 5 }),
          getWeeklyRanking({ category, limit: 5 }),
        ]);

        if (active) {
          setEvents(eventList);
          setHotEvents(whatsHot);
          setRankingEvents(weeklyRanking);
        }
      } catch (error) {
        if (active) {
          const message = axios.isAxiosError(error)
            ? error.response?.data?.message
            : null;
          setErrorMessage(message ?? "이벤트를 불러오지 못했습니다.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadPage();

    return () => {
      active = false;
    };
  }, [category]);

  const heroSlides = hotEvents.filter(hasBannerImage).slice(0, 3).map((event) => ({
    id: event.id,
    name: event.name,
    imageUrl: event.bannerImageUrl,
    posterUrl: event.mainImageUrl,
  }));

  const closingSoonEvents = [...events].sort(
    (firstEvent, secondEvent) =>
      new Date(firstEvent.endDate).getTime() - new Date(secondEvent.endDate).getTime(),
  );

  return (
    <main className="min-h-screen bg-white text-[#222222]">
      {!loading && !errorMessage && <SlidePosts slides={heroSlides} />}

      <section className="py-20 text-center">
        <h1 className="text-4xl font-extrabold">{categoryName}</h1>
        <div className="mx-auto mt-5 h-[3px] w-10 bg-[#f36f21]" />
      </section>

      {loading ? (
        <p className="py-32 text-center text-gray-500">
          이벤트를 불러오는 중입니다.
        </p>
      ) : errorMessage ? (
        <p className="py-32 text-center text-red-500">{errorMessage}</p>
      ) : events.length === 0 ? (
        <p className="py-32 text-center text-gray-500">
          등록된 이벤트가 없습니다.
        </p>
      ) : (
        <>
          <section className="mb-24">
            <PosterSection title="WHAT'S HOT" events={hotEvents} />
          </section>

          <section className="mb-24 bg-[#f7f7f7] py-20">
            <WeeklyRankingSection events={rankingEvents} />
          </section>

          <section className="mb-24 bg-[#f7f7f7] py-20">
            <PosterSection
              title="마감 임박!"
              events={closingSoonEvents.slice(0, 5)}
              showMore={false}
            />
          </section>

          <section className="pb-24">
            <PosterSection title="FREE TICKET'S PICKS" events={events} />
          </section>
        </>
      )}
    </main>
  );
}
