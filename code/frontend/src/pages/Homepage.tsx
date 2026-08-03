import { useEffect, useState } from "react";
import axios from "axios";

import { getWhatsHot, hasBannerImage } from "../api/events";
import Banner from "../components/Banner";
import SlidePosts from "../components/SlidePosts1";
import type { RankedEvent } from "../types/Event";

export default function Homepage() {
  const [events, setEvents] = useState<RankedEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    const loadEvents = async () => {
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
  }, []);

  if (loading) {
    return (
      <main className="flex min-h-[500px] items-center justify-center text-gray-500">
        이벤트를 불러오는 중입니다.
      </main>
    );
  }

  if (errorMessage) {
    return (
      <main className="flex min-h-[500px] items-center justify-center px-6 text-center text-red-500">
        {errorMessage}
      </main>
    );
  }

  const heroSlides = events.filter(hasBannerImage).slice(0, 3).map((event) => ({
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
