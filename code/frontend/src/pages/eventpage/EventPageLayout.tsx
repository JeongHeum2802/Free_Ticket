import { useEffect, useState } from "react";
import axios from "axios";

import { getEvents, getWeeklyRanking, getWhatsHot } from "../../api/events";
import SlidePosts from "../../components/SlidePosts1";
import type {
  EventCategory,
  EventSummary,
  HotEvent,
  WeeklyRankedEvent,
} from "../../types/Event";
import PosterSection from "./PosterSection";
import WeeklyRankingSection from "./WeeklyRankingSection";

type EventPageLayoutProps = {
  category: EventCategory;
  categoryName: string;
};

type SectionMessageProps = {
  message: string;
  error?: boolean;
};

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message ?? fallback;
  }

  return fallback;
}

function SectionMessage({ message, error = false }: SectionMessageProps) {
  return (
    <p className={`py-20 text-center ${error ? "text-red-500" : "text-gray-500"}`}>
      {message}
    </p>
  );
}

export default function EventPageLayout({
  category,
  categoryName,
}: EventPageLayoutProps) {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [hotEvents, setHotEvents] = useState<HotEvent[]>([]);
  const [rankingEvents, setRankingEvents] = useState<WeeklyRankedEvent[]>([]);
  const [eventsError, setEventsError] = useState<string | null>(null);
  const [hotEventsError, setHotEventsError] = useState<string | null>(null);
  const [rankingError, setRankingError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    const loadPage = async () => {
      setLoading(true);
      setEvents([]);
      setHotEvents([]);
      setRankingEvents([]);
      setEventsError(null);
      setHotEventsError(null);
      setRankingError(null);

      const [eventListResult, whatsHotResult, weeklyRankingResult] =
        await Promise.allSettled([
          getEvents(category),
          getWhatsHot({ category, limit: 5 }),
          getWeeklyRanking({ category, limit: 5 }),
        ]);

      if (!active) {
        return;
      }

      if (eventListResult.status === "fulfilled") {
        setEvents(eventListResult.value);
      } else {
        setEventsError(
          getErrorMessage(eventListResult.reason, "이벤트 목록을 불러오지 못했습니다."),
        );
      }

      if (whatsHotResult.status === "fulfilled") {
        setHotEvents(whatsHotResult.value);
      } else {
        setHotEventsError(
          getErrorMessage(whatsHotResult.reason, "인기 이벤트를 불러오지 못했습니다."),
        );
      }

      if (weeklyRankingResult.status === "fulfilled") {
        setRankingEvents(weeklyRankingResult.value);
      } else {
        setRankingError(
          getErrorMessage(
            weeklyRankingResult.reason,
            "주간 이벤트 순위를 불러오지 못했습니다.",
          ),
        );
      }

      setLoading(false);
    };

    void loadPage();

    return () => {
      active = false;
    };
  }, [category]);

  const heroSlides = hotEvents
    .filter((event) => event.bannerImageUrl.trim() && event.mainImageUrl.trim())
    .slice(0, 3)
    .map((event) => ({
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
      {!loading && heroSlides.length > 0 && <SlidePosts slides={heroSlides} />}

      <section className="py-20 text-center">
        <h1 className="text-4xl font-extrabold">{categoryName}</h1>
        <div className="mx-auto mt-5 h-[3px] w-10 bg-[#f36f21]" />
      </section>

      {loading ? (
        <SectionMessage message="이벤트를 불러오는 중입니다." />
      ) : (
        <>
          <section className="mb-24">
            {hotEventsError ? (
              <SectionMessage message={hotEventsError} error />
            ) : hotEvents.length > 0 ? (
              <PosterSection title="WHAT'S HOT" events={hotEvents} />
            ) : (
              <SectionMessage message="등록된 인기 이벤트가 없습니다." />
            )}
          </section>

          <section className="mb-24 bg-[#f7f7f7] py-20">
            {rankingError ? (
              <SectionMessage message={rankingError} error />
            ) : rankingEvents.length > 0 ? (
              <WeeklyRankingSection events={rankingEvents} />
            ) : (
              <SectionMessage message="등록된 주간 순위가 없습니다." />
            )}
          </section>

          {eventsError ? (
            <SectionMessage message={eventsError} error />
          ) : events.length === 0 ? (
            <SectionMessage message="등록된 이벤트가 없습니다." />
          ) : (
            <>
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
        </>
      )}
    </main>
  );
}
