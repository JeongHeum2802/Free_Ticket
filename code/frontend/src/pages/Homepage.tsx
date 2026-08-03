import { useEffect, useState } from "react";
import axios from "axios";

import { getWhatsHot } from "../api/events";
import Banner from "../components/Banner";
import SlidePosts from "../components/SlidePosts1";
import type { HotEvent } from "../types/Event";

export default function Homepage() {
  const [events, setEvents] = useState<HotEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    const loadEvents = async () => {
      setLoading(true);
      setErrorMessage(null);

      try {
        const nextEvents = await getWhatsHot({ limit: 7 });

        if (active) {
          setEvents(nextEvents);
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

    void loadEvents();

    return () => {
      active = false;
    };
  }, [reloadKey]);

  if (loading) {
    return (
      <main className="flex min-h-[500px] items-center justify-center text-gray-500">
        이벤트를 불러오는 중입니다.
      </main>
    );
  }

  if (errorMessage) {
    return (
      <main className="flex min-h-[500px] flex-col items-center justify-center gap-4 px-6 text-center">
        <p className="text-red-500">{errorMessage}</p>
        <button
          type="button"
          onClick={() => setReloadKey((key) => key + 1)}
          className="rounded-lg border border-gray-300 px-5 py-2 text-sm font-bold hover:bg-gray-50"
        >
          다시 시도
        </button>
      </main>
    );
  }

  if (events.length === 0) {
    return (
      <main className="flex min-h-[500px] items-center justify-center text-gray-500">
        등록된 인기 이벤트가 없습니다.
      </main>
    );
  }

  const heroSlides = events
    .filter((event) => event.bannerImageUrl.trim() && event.mainImageUrl.trim())
    .slice(0, 3)
    .map((event) => ({
      id: event.id,
      name: event.name,
      imageUrl: event.bannerImageUrl,
      posterUrl: event.mainImageUrl,
    }));

  return (
    <main>
      <SlidePosts slides={heroSlides} />
      <Banner title="WHAT'S HOT" events={events} />
    </main>
  );
}
