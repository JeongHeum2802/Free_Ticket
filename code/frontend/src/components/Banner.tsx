import { Link } from "react-router-dom";
import type { HotEvent } from "../types/Event";

type BannerProps = {
  title?: string;
  events: HotEvent[];
};

export default function Banner({ title, events }: BannerProps) {
  const visibleEvents = events.filter((event) => event.mainImageUrl.trim());
  const [featuredEvent, ...otherEvents] = visibleEvents;

  if (!featuredEvent) {
    return (
      <section className="px-7 py-20 text-center text-gray-500">
        표시할 인기 이벤트가 없습니다.
      </section>
    );
  }

  return (
    <section className="p-7">
      <header className="flex h-38 items-center justify-center text-[35px] font-bold text-[#333]">
        {title}
      </header>

      <div className="grid grid-cols-2 gap-4 p-7 md:grid-cols-5 md:grid-rows-2">
        <Link
          to={`/ticket/${featuredEvent.id}`}
          className="col-span-2 overflow-hidden md:row-span-2"
        >
          <img
            src={featuredEvent.mainImageUrl}
            alt={`${featuredEvent.name} 포스터`}
            className="h-full w-full object-cover transition-transform duration-300 hover:scale-105"
          />
        </Link>

        {otherEvents.map((event) => (
          <Link key={event.id} to={`/ticket/${event.id}`} className="overflow-hidden">
            <img
              src={event.mainImageUrl}
              alt={`${event.name} 포스터`}
              className="h-full w-full object-cover transition-transform duration-300 hover:scale-105"
            />
          </Link>
        ))}
      </div>
    </section>
  );
}
