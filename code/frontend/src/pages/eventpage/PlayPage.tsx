import EventPageLayout from "./EventPageLayout";
import type { EventPageItem } from "./EventPageTypes";

import type { Concert } from "../../types/Concert";
const playEvents: EventPageItem[] = [
  {
    eventid: 201,
    name: "그날의 약속",
    category: "play",
    start_time: new Date("2026-08-07T18:30:00"),
    end_time: new Date("2026-08-07T20:30:00"),
    location: "서울특별시 종로구 동숭길 122",
    postUrl: "",
    imageUrl: "",
  },
  {
    eventid: 202,
    name: "우리 집에 낯선 사람이 산다",
    category: "play",
    start_time: new Date("2026-08-22T15:00:00"),
    end_time: new Date("2026-08-22T17:00:00"),
    location: "경기도 성남시 분당구 성남대로 808",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615554/event-201-post_ojcvcu.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615554/event-201-banner_tmlerr.png",
  },
  {
    eventid: 203,
    name: "비 오는 날의 기억",
    category: "play",
    start_time: new Date("2026-09-11T19:00:00"),
    end_time: new Date("2026-09-11T21:00:00"),
    location: "광주광역시 동구 예술길 31-13",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615555/event-203-post_ouxvlg.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615556/event-203-banner_tilb8q.png",
  },
  {
    eventid: 204,
    name: "두 번째 봄",
    category: "play",
    start_time: new Date("2026-10-02T19:30:00"),
    end_time: new Date("2026-10-02T21:30:00"),
    location: "대전광역시 서구 둔산대로 135",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615557/event-204-post_zprpty.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615559/event-204-banner_swps6k.png",
  },
  {
    eventid: 205,
    name: "옥상 위의 대화",
    category: "play",
    start_time: new Date("2026-08-25T19:00:00"),
    end_time: new Date("2026-08-25T21:00:00"),
    location: "서울특별시 마포구 양화로 45",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715090/205-poster_hy2i54.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715200/205-banner_wefopq.png",
  },
  {
    eventid: 206,
    name: "사라진 목격자",
    category: "play",
    start_time: new Date("2026-09-17T19:30:00"),
    end_time: new Date("2026-09-17T21:30:00"),
    location: "부산광역시 남구 유엔평화로 76번길 1",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/206-poster_rmttly.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715199/206-banner_gvzqrg.png",
  },
  {
    eventid: 207,
    name: "아버지의 오래된 시계",
    category: "play",
    start_time: new Date("2026-10-15T18:30:00"),
    end_time: new Date("2026-10-15T20:30:00"),
    location: "대구광역시 달서구 공원순환로 201",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/207-poster_oivccf.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715199/207-banner_hkiplk.png",
  },
  {
    eventid: 208,
    name: "세입자들",
    category: "play",
    start_time: new Date("2026-11-13T20:00:00"),
    end_time: new Date("2026-11-13T22:00:00"),
    location: "대전광역시 중구 중앙로 32",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/208-poster_s9ia26.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715199/208-banner_utvlpl.png",
  },
  {
    eventid: 209,
    name: "오후 3시의 고양이",
    category: "play",
    start_time: new Date("2026-08-10T16:00:00"),
    end_time: new Date("2026-08-10T17:40:00"),
    location: "서울특별시 종로구 대학로12길 21",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707133/play1-2_vzznlw.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707133/play1-1_wz5ezh.png",
  },
  {
    eventid: 210,
    name: "우리가 잃어버린 것들",
    category: "play",
    start_time: new Date("2026-08-25T19:30:00"),
    end_time: new Date("2026-08-25T21:00:00"),
    location: "서울특별시 종로구 대학로8가길 85",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707133/play2-2_pepin1.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707133/play2-1_ghgqte.png",
  },
  {
    eventid: 211,
    name: "정거장에서 만난 사람",
    category: "play",
    start_time: new Date("2026-09-05T18:00:00"),
    end_time: new Date("2026-09-05T19:30:00"),
    location: "서울특별시 종로구 대학로12길 83",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707134/play3-2_yikvdq.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707134/play3-1_rhhefb.png",
  },
  {
    eventid: 212,
    name: "빈 방의 속삭임",
    category: "play",
    start_time: new Date("2026-10-01T20:00:00"),
    end_time: new Date("2026-10-01T21:40:00"),
    location: "서울특별시 종로구 동숭길 148",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707134/play4-2_ukwb4t.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707134/play4-1_pt6uye.png",
  },
  {
    eventid: 213,
    name: "세월의 무게: 잊혀진 약속",
    category: "play",
    start_time: new Date("2026-08-12T15:00:00"),
    end_time: new Date("2026-08-12T16:40:00"),
    location: "서울특별시 종로구 대학로10길 17",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717778/%EC%97%B0%EA%B7%B91_%EA%B8%B0%EB%B3%B8_gn7jag.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717812/%EC%97%B0%EA%B7%B91_%EB%B0%B0%EB%84%88_itbnyy.png",
  },
  {
    eventid: 214,
    name: "그림자 속의 목격자",
    category: "play",
    start_time: new Date("2026-08-28T19:30:00"),
    end_time: new Date("2026-08-28T21:00:00"),
    location: "서울특별시 종로구 대학로8가길 18",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717780/%EC%97%B0%EA%B7%B92_%EA%B8%B0%EB%B3%B8_xpek2r.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717813/%EC%97%B0%EA%B7%B92_%EB%B0%B0%EB%84%88_cancqr.png",
  },
  {
    eventid: 215,
    name: "닫힌 문 너머 그녀의 침묵",
    category: "play",
    start_time: new Date("2026-09-10T18:00:00"),
    end_time: new Date("2026-09-10T19:30:00"),
    location: "서울특별시 종로구 동숭길 25",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717779/%EC%97%B0%EA%B7%B93_%EA%B8%B0%EB%B3%B8_eixdy7.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717810/%EC%97%B0%EA%B7%B93_%EB%B0%B0%EB%84%88_rwchhv.png",
  },
  {
    eventid: 216,
    name: "그림자 속으로",
    category: "play",
    start_time: new Date("2026-10-05T20:00:00"),
    end_time: new Date("2026-10-05T21:40:00"),
    location: "서울특별시 종로구 대학로12길 15",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717778/%EC%97%B0%EA%B7%B94_%EA%B8%B0%EB%B3%B8_w3howk.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717810/%EC%97%B0%EA%B7%B94_%EB%B0%B0%EB%84%88_tmcavi.png",
  },
];

const weeklyRankingIds = [213, 209, 205, 203, 214];

const weeklyRankingEvents = weeklyRankingIds
  .map((eventid) =>
    playEvents.find((event) => event.eventid === eventid)
  )
  .filter(
    (event): event is EventPageItem => event !== undefined
  );

export default function PlayPage() {
  return (
    <EventPageLayout
      categoryName="연극"
      events={playEvents}
      weeklyRankingEvents={weeklyRankingEvents}
    />
  );
}