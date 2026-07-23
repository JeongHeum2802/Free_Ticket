import EventPageLayout from "./EventPageLayout";
import type { EventPageItem } from "./EventPageTypes";

import type { Concert } from "../../types/Concert";
const musicalEvents: EventPageItem[] = [
  {
    eventid: 101,
    name: "별빛 아래 우리",
    category: "musical",
    start_time: new Date("2026-08-05T19:30:00"),
    end_time: new Date("2026-08-05T22:00:00"),
    location: "서울특별시 종로구 대학로 12길 21",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615553/event-101-post_vwma6m.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615552/event-101-banner_a9gomg.png",
  },
  {
    eventid: 102,
    name: "시간을 걷는 소녀",
    category: "musical",
    start_time: new Date("2026-08-12T19:00:00"),
    end_time: new Date("2026-08-12T21:30:00"),
    location: "서울특별시 송파구 올림픽로 300",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615551/event-102-post_xukb3x.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615553/event-102-banner_a5ornu.png",
  },
  {
    eventid: 103,
    name: "푸른 달의 노래",
    category: "musical",
    start_time: new Date("2026-09-03T20:00:00"),
    end_time: new Date("2026-09-03T22:30:00"),
    location: "부산광역시 남구 유엔평화로 76번길 1",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615554/event-103-post_w1yve0.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615553/event-103-banner_zumagn.png",
  },
  {
    eventid: 104,
    name: "마지막 편지",
    category: "musical",
    start_time: new Date("2026-09-18T19:30:00"),
    end_time: new Date("2026-09-18T22:00:00"),
    location: "대구광역시 북구 호암로 15",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615553/event-104-post_wcwxph.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615553/event-104-banner_v7gdki.png",
  },
  {
    eventid: 105,
    name: "달빛 정거장",
    category: "musical",
    start_time: new Date("2026-08-20T19:30:00"),
    end_time: new Date("2026-08-20T22:00:00"),
    location: "서울특별시 종로구 대학로8길 7",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715090/105-poster_mhe6y0.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715201/105-banner_nzhpiy.png",
  },
  {
    eventid: 106,
    name: "꿈을 파는 상점",
    category: "musical",
    start_time: new Date("2026-09-09T19:00:00"),
    end_time: new Date("2026-09-09T21:30:00"),
    location: "경기도 성남시 분당구 성남대로 808",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715090/106-poster_dr3ove.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715200/106-banner_cpkwzr.png",
  },
  {
    eventid: 107,
    name: "겨울이 오기 전에",
    category: "musical",
    start_time: new Date("2026-10-08T20:00:00"),
    end_time: new Date("2026-10-08T22:30:00"),
    location: "인천광역시 남동구 예술로 149",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715090/107-poster_lz6ni8.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715200/107-banner_knyhtl.png",
  },
  {
    eventid: 108,
    name: "붉은 장미의 비밀",
    category: "musical",
    start_time: new Date("2026-11-06T19:30:00"),
    end_time: new Date("2026-11-06T22:00:00"),
    location: "광주광역시 북구 북문대로 60",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715090/108-poster_bupisr.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715200/108-banner_tuplsb.png",
  },
  {
    eventid: 109,
    name: "기억의 파편",
    category: "musical",
    start_time: new Date("2026-08-20T19:30:00"),
    end_time: new Date("2026-08-20T22:00:00"),
    location: "서울특별시 중구 퇴계로 387",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707135/%EB%AE%A4%EC%A7%80%EC%BB%AC1-2_o5dj70.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707135/%EB%AE%A4%EC%A7%80%EC%BB%AC1-1_isje84.png",
  },
  {
    eventid: 110,
    name: "푸른 수염의 비밀",
    category: "musical",
    start_time: new Date("2026-09-01T20:00:00"),
    end_time: new Date("2026-09-01T22:30:00"),
    location: "서울특별시 구로구 경인로 662",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707133/%EB%AE%A4%EC%A7%80%EC%BB%AC2-2_llnl8d.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707133/%EB%AE%A4%EC%A7%80%EC%BB%AC2-1_fjg5ap.png",
  },
  {
    eventid: 111,
    name: "시간의 나침반",
    category: "musical",
    start_time: new Date("2026-08-15T14:00:00"),
    end_time: new Date("2026-08-15T16:30:00"),
    location: "서울특별시 송파구 올림픽로 240",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707134/%EB%AE%A4%EC%A7%80%EC%BB%AC3-2_erjp54.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707134/%EB%AE%A4%EC%A7%80%EC%BB%AC3-1_taavcw.png",
  },
  {
    eventid: 112,
    name: "어둠 속의 왈츠",
    category: "musical",
    start_time: new Date("2026-10-10T19:00:00"),
    end_time: new Date("2026-10-10T21:30:00"),
    location: "서울특별시 용산구 이태원로 294",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707135/%EB%AE%A4%EC%A7%80%EC%BB%AC4-2_kchtlz.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707135/%EB%AE%A4%EC%A7%80%EC%BB%AC4-1_snh9bp.png",
  },
  {
    eventid: 113,
    name: "운명의 랩소디",
    category: "musical",
    start_time: new Date("2026-08-10T19:30:00"),
    end_time: new Date("2026-08-10T22:00:00"),
    location: "서울특별시 서초구 반포대로 211",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717636/%EB%AE%A4%EC%A7%80%EC%BB%AC1_%EA%B8%B0%EB%B3%B8_l5hz91.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717684/%EB%AE%A4%EC%A7%80%EC%BB%AC1_%EB%B0%B0%EB%84%88_uyve1y.png",
  },
  {
    eventid: 114,
    name: "달빛 연가: 제국의 시작",
    category: "musical",
    start_time: new Date("2026-09-15T19:00:00"),
    end_time: new Date("2026-09-15T21:30:00"),
    location: "서울특별시 종로구 대학로 104",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717635/%EB%AE%A4%EC%A7%80%EC%BB%AC2_%EA%B8%B0%EB%B3%B8_m5j3bl.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717683/%EB%AE%A4%EC%A7%80%EC%BB%AC2_%EB%B0%B0%EB%84%88_r4okyg.png",
  },
  {
    eventid: 115,
    name: "새벽의 운명: 용의 연가",
    category: "musical",
    start_time: new Date("2026-10-02T14:00:00"),
    end_time: new Date("2026-10-02T16:30:00"),
    location: "서울특별시 송파구 올림픽로 300",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717662/%EB%AE%A4%EC%A7%80%EC%BB%AC3_%EA%B8%B0%EB%B3%B8_bwmu58.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717718/%EB%AE%A4%EC%A7%80%EC%BB%AC3_%EB%B0%B0%EB%84%88_twykcj.png",
  },
  {
    eventid: 116,
    name: "밤의 기적: 운명의 연가",
    category: "musical",
    start_time: new Date("2026-11-01T19:00:00"),
    end_time: new Date("2026-11-01T21:30:00"),
    location: "서울특별시 용산구 이태원로 294",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717641/%EB%AE%A4%EC%A7%80%EC%BB%AC4_%EA%B8%B0%EB%B3%B8_swcl5z.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717686/%EB%AE%A4%EC%A7%80%EC%BB%AC4_%EB%B0%B0%EB%84%88_dxmmnw.png",
  },
];

const weeklyRankingIds = [113, 109, 105, 101, 114];

const weeklyRankingEvents = weeklyRankingIds
  .map((eventid) =>
    musicalEvents.find((event) => event.eventid === eventid)
  )
  .filter(
    (event): event is EventPageItem => event !== undefined
  );

export default function MusicalPage() {
  return (
    <EventPageLayout
      categoryName="뮤지컬"
      events={musicalEvents}
      weeklyRankingEvents={weeklyRankingEvents}
    />
  );
}