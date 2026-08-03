import { Link } from "react-router-dom";

import type { EventSummary } from "../../types/Event";

type PosterSectionProps = {
  title: string;
  events: EventSummary[];
  showMore?: boolean;
};

/*
  날짜를 2026.08.15 형식으로 표시합니다.
*/
function formatDate(value: string): string {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}.${month}.${day}`;
}

/*
  시작일과 종료일이 같으면 날짜 하나만 표시합니다.

  2026.08.15

  날짜가 다르면 기간으로 표시합니다.

  2026.08.15 ~ 2026.11.15
*/
function formatEventPeriod(
  startDate: string,
  endDate: string
): string {
  const formattedStartDate = formatDate(startDate);
  const formattedEndDate = formatDate(endDate);

  if (!formattedStartDate || !formattedEndDate) {
    return "";
  }

  if (formattedStartDate === formattedEndDate) {
    return formattedStartDate;
  }

  return `${formattedStartDate} ~ ${formattedEndDate}`;
}

export default function PosterSection({
  title,
  events,
  showMore = true,
}: PosterSectionProps) {
  return (
    <section className="mx-auto w-full max-w-[1100px] px-6">
      {/* 섹션 제목 */}
      <header className="mb-8 flex items-center justify-between">
        <h2 className="text-[28px] font-extrabold tracking-[-0.5px] text-[#222]">
          {title}
        </h2>

        {showMore && (
          <button
            type="button"
            className="
              border border-[#dddddd]
              bg-white
              px-4 py-2
              text-sm text-[#555555]
              transition-colors
              hover:border-[#222222]
              hover:text-[#222222]
            "
          >
            더보기 +
          </button>
        )}
      </header>

      {/* 포스터 목록 */}
      <div className="grid grid-cols-2 gap-x-5 gap-y-10 md:grid-cols-3 lg:grid-cols-5">
        {events.map((event) => (
          <Link
            key={event.id}
            to={`/ticket/${event.id}`}
            className="group min-w-0 no-underline"
          >
            {/* 포스터 이미지 */}
            <div className="relative aspect-[3/4] overflow-hidden bg-[#eeeeee]">
              <img
                src={event.mainImageUrl}
                alt={`${event.name} 포스터`}
                className="
                  h-full w-full
                  object-cover
                  transition-transform
                  duration-300
                  group-hover:scale-[1.045]
                "
              />
            </div>

            {/* 포스터 하단 정보 */}
            <div className="pt-4 text-center">
              <h3
                className="
                  line-clamp-2
                  min-h-[46px]
                  text-[15px]
                  font-medium
                  leading-[1.5]
                  text-[#222222]
                "
              >
                {event.name}
              </h3>

              <p className="mt-2 text-[13px] text-[#999999]">
                {formatEventPeriod(
                  event.startDate,
                  event.endDate
                )}
              </p>

              <p
                className="
                  mt-1
                  overflow-hidden
                  text-ellipsis
                  whitespace-nowrap
                  text-[13px]
                  font-semibold
                  text-[#f36f21]
                "
                title={event.location}
              >
                {event.location}
              </p>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
