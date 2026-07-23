import { Link } from "react-router-dom";

import type { EventPageItem } from "./EventPageTypes";

type WeeklyRankingSectionProps = {
  events: EventPageItem[];
};

function formatDate(value: Date | string): string {
  const date =
    value instanceof Date
      ? value
      : new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const year = date.getFullYear();
  const month = String(
    date.getMonth() + 1
  ).padStart(2, "0");
  const day = String(
    date.getDate()
  ).padStart(2, "0");

  return `${year}.${month}.${day}`;
}

function formatEventPeriod(
  startTime: Date | string,
  endTime: Date | string
): string {
  const startDate = formatDate(startTime);
  const endDate = formatDate(endTime);

  if (!startDate || !endDate) {
    return "";
  }

  if (startDate === endDate) {
    return startDate;
  }

  return `${startDate} ~ ${endDate}`;
}

export default function WeeklyRankingSection({
  events,
}: WeeklyRankingSectionProps) {

  const rankingEvents = events.slice(0, 5);

  const [firstEvent, ...otherEvents] =
    rankingEvents;

  if (!firstEvent) {
    return null;
  }

  return (
    <div className="mx-auto w-full max-w-[1100px] px-6">
      {/* 제목 영역 */}
      <header className="mb-8">
        <p className="mb-2 text-sm font-bold tracking-[0.2em] text-[#f36f21]">
          THIS WEEK
        </p>

        <h2 className="text-[28px] font-extrabold tracking-[-0.5px] text-[#222222]">
          WEEKLY RANKING
        </h2>
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        {/* 1위 이벤트 */}
        <Link
          to={`/ticket/${firstEvent.eventid}`}
          className="
            group
            grid
            overflow-hidden
            border border-[#eeeeee]
            bg-white
            shadow-sm
            no-underline
            sm:grid-cols-[240px_minmax(0,1fr)]
          "
        >
          {/* 1위 포스터 영역 */}
          <div
            className="
              flex
              items-center
              justify-center
              border-b border-[#eeeeee]
              bg-white
              p-4
              sm:border-b-0
              sm:border-r
            "
          >
            <div
              className="
                relative
                aspect-[3/4]
                w-full
                max-w-[220px]
                overflow-hidden
                bg-white
              "
            >
              <img
                src={firstEvent.postUrl}
                alt={`${firstEvent.name} 포스터`}
                className="
                  block
                  h-full w-full
                  object-contain
                  transition-transform
                  duration-300
                  group-hover:scale-[1.035]
                "
              />

              {/* 1위 표시 */}
              <span
                className="
                  absolute left-3 top-3
                  flex h-12 w-12
                  items-center justify-center
                  rounded-full
                  bg-[#f36f21]
                  text-xl font-extrabold
                  text-white
                  shadow-sm
                "
              >
                1
              </span>
            </div>
          </div>

          {/* 1위 정보 영역 */}
          <div className="flex min-w-0 flex-col justify-center p-7">
            <p className="mb-3 text-sm font-bold text-[#f36f21]">
              이번 주 1위
            </p>

            <h3 className="text-[24px] font-extrabold leading-[1.4] text-[#222222]">
              {firstEvent.name}
            </h3>

            <p className="mt-5 text-sm text-[#888888]">
              {formatEventPeriod(
                firstEvent.start_time,
                firstEvent.end_time
              )}
            </p>

            <p className="mt-2 text-sm font-semibold leading-[1.6] text-[#555555]">
              {firstEvent.location}
            </p>

            <span className="mt-8 text-sm font-bold text-[#222222]">
              상세보기 →
            </span>
          </div>
        </Link>

        {/* 2~5위 이벤트 */}
        <ol
          className="
            m-0
            list-none
            overflow-hidden
            border border-[#eeeeee]
            bg-white
            p-0
            shadow-sm
          "
        >
          {otherEvents.map((event, index) => {
            const rank = index + 2;

            return (
              <li
                key={event.eventid}
                className="
                  border-b border-[#eeeeee]
                  last:border-b-0
                "
              >
                <Link
                  to={`/ticket/${event.eventid}`}
                  className="
                    group
                    grid
                    grid-cols-[40px_64px_minmax(0,1fr)]
                    items-center
                    gap-4
                    px-5 py-4
                    no-underline
                    transition-colors
                    hover:bg-[#fafafa]
                  "
                >
                  {/* 순위 숫자 */}
                  <strong className="text-center text-lg font-extrabold text-[#555555]">
                    {rank}
                  </strong>

                  {/* 작은 포스터 */}
                  <div
                    className="
                      aspect-[3/4]
                      overflow-hidden
                      bg-white
                    "
                  >
                    <img
                      src={event.postUrl}
                      alt={`${event.name} 포스터`}
                      className="
                        block
                        h-full w-full
                        object-contain
                        transition-transform
                        duration-300
                        group-hover:scale-[1.05]
                      "
                    />
                  </div>

                  {/* 이벤트 정보 */}
                  <div className="min-w-0">
                    <h3
                      className="
                        overflow-hidden
                        text-ellipsis
                        whitespace-nowrap
                        text-[15px]
                        font-bold
                        text-[#222222]
                      "
                    >
                      {event.name}
                    </h3>

                    <p className="mt-2 text-[12px] text-[#999999]">
                      {formatEventPeriod(
                        event.start_time,
                        event.end_time
                      )}
                    </p>

                    <p
                      className="
                        mt-1
                        overflow-hidden
                        text-ellipsis
                        whitespace-nowrap
                        text-[12px]
                        text-[#777777]
                      "
                      title={event.location}
                    >
                      {event.location}
                    </p>
                  </div>
                </Link>
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}