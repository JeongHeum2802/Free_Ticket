import EventPageLayout from "./EventPageLayout";
import type { EventPageItem } from "./EventPageTypes";

const concertEvents: EventPageItem[] = [
  {
    eventid: 401,
    name: "여름밤의 멜로디",
    category: "concert",
    start_time: new Date("2026-08-08T18:00:00"),
    end_time: new Date("2026-08-08T21:00:00"),
    location: "서울특별시 송파구 올림픽로 424",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615559/event-401-post_mm8z7x.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615561/event-401-banner_obu2ku.png",
  },
  {
    eventid: 402,
    name: "청춘 록 페스티벌",
    category: "concert",
    start_time: new Date("2026-08-29T16:00:00"),
    end_time: new Date("2026-08-29T22:00:00"),
    location: "인천광역시 연수구 센트럴로 350",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615559/event-402-post_dvai4e.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615561/event-402-banner_bnlgsw.png",
  },
  {
    eventid: 403,
    name: "다시 만나는 우리",
    category: "concert",
    start_time: new Date("2026-09-26T19:00:00"),
    end_time: new Date("2026-09-26T21:30:00"),
    location: "경기도 고양시 일산서구 킨텍스로 217-60",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615560/event-403-post_pkew50.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615561/event-403-banner_n1wf2a.png",
  },
  {
    eventid: 404,
    name: "가을 감성 음악회",
    category: "concert",
    start_time: new Date("2026-10-17T18:30:00"),
    end_time: new Date("2026-10-17T21:00:00"),
    location: "부산광역시 해운대구 수영강변대로 120",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615561/event-404-post_d7ou99.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615561/event-404-banner_ia17ry.png",
  },
  {
    eventid: 405,
    name: "네온 시티 라이브",
    category: "concert",
    start_time: new Date("2026-08-21T19:00:00"),
    end_time: new Date("2026-08-21T21:30:00"),
    location: "서울특별시 광진구 구천면로 20",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715087/405-poster_k3iyjl.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715197/405-banner_xjlpdj.png",
  },
  {
    eventid: 406,
    name: "별이 쏟아지는 밤",
    category: "concert",
    start_time: new Date("2026-09-19T18:30:00"),
    end_time: new Date("2026-09-19T21:00:00"),
    location: "경기도 가평군 가평읍 문화로 131",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715087/406-poster_ihnoje.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715197/406-banner_vpting.png",
  },
  {
    eventid: 407,
    name: "도시의 파동",
    category: "concert",
    start_time: new Date("2026-10-31T20:00:00"),
    end_time: new Date("2026-10-31T22:30:00"),
    location: "인천광역시 연수구 아트센터대로 222",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715087/407-poster_kxtiwx.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715196/407-banner_mweiep.png",
  },
  {
    eventid: 408,
    name: "우리들의 마지막 앙코르",
    category: "concert",
    start_time: new Date("2026-11-28T18:00:00"),
    end_time: new Date("2026-11-28T21:00:00"),
    location: "대구광역시 북구 엑스코로 10",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/408-poster_tmqair.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715196/408-banner_hdwrdd.png",
  },
  {
    eventid: 409,
    name: "한여름의 로맨틱 밴드",
    category: "concert",
    start_time: new Date("2026-08-22T18:00:00"),
    end_time: new Date("2026-08-22T21:00:00"),
    location: "서울특별시 송파구 올림픽로 424",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707138/%EC%BD%98%EC%84%9C%ED%8A%B81-2_nwpik5.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707138/%EC%BD%98%EC%84%9C%ED%8A%B81-1_rw1qzx.png",
  },
  {
    eventid: 410,
    name: "인디 바이브 페스타 2026",
    category: "concert",
    start_time: new Date("2026-09-12T16:00:00"),
    end_time: new Date("2026-09-12T22:00:00"),
    location: "서울특별시 광진구 구천면로 20",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707138/%EC%BD%98%EC%84%9C%ED%8A%B82-2_jemtjo.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707138/%EC%BD%98%EC%84%9C%ED%8A%B82-1_ufmlix.png",
  },
  {
    eventid: 411,
    name: "별 헤는 밤, 어쿠스틱 콘서트",
    category: "concert",
    start_time: new Date("2026-09-26T19:00:00"),
    end_time: new Date("2026-09-26T21:30:00"),
    location: "서울특별시 성북구 안암로 145",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707140/%EC%BD%98%EC%84%9C%ED%8A%B83-2_iy2pwc.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707138/%EC%BD%98%EC%84%9C%ED%8A%B83-1_rbvk6c.png",
  },
  {
    eventid: 412,
    name: "뜨거운 안녕, 가을 록 파티",
    category: "concert",
    start_time: new Date("2026-10-17T18:00:00"),
    end_time: new Date("2026-10-17T21:00:00"),
    location: "서울특별시 송파구 올림픽로 25",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707139/%EC%BD%98%EC%84%9C%ED%8A%B84-2_gvqvod.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707139/%EC%BD%98%EC%84%9C%ED%8A%B84-1_sfkg0i.png",
  },
  {
    eventid: 413,
    name: "우리가 빛나는 시간",
    category: "concert",
    start_time: new Date("2026-08-25T19:00:00"),
    end_time: new Date("2026-08-25T22:00:00"),
    location: "서울특별시 송파구 올림픽로 424",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718122/%EC%BD%98%EC%84%9C%ED%8A%B81_%EA%B8%B0%EB%B3%B8_zird3b.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718064/%EC%BD%98%EC%84%9C%ED%8A%B81_%EB%B0%B0%EB%84%88_dyohdf.jpg",
  },
  {
    eventid: 414,
    name: "달빛 아래, 우리의 노래",
    category: "concert",
    start_time: new Date("2026-09-18T18:00:00"),
    end_time: new Date("2026-09-18T21:00:00"),
    location: "서울특별시 마포구 월드컵로 240",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718124/%EC%BD%98%EC%84%9C%ED%8A%B82_%EA%B8%B0%EB%B3%B8_rbdimo.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718061/%EC%BD%98%EC%84%9C%ED%8A%B82_%EB%B0%B0%EB%84%88_l6hqai.jpg",
  },
  {
    eventid: 415,
    name: "네온 스케치 우리의 빛나는 순간",
    category: "concert",
    start_time: new Date("2026-09-30T19:30:00"),
    end_time: new Date("2026-09-30T22:00:00"),
    location: "서울특별시 강남구 영동대로 513",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718125/%EC%BD%98%EC%84%9C%ED%8A%B83_%EA%B8%B0%EB%B3%B8_yothy3.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718103/%EC%BD%98%EC%84%9C%ED%8A%B83_%EB%B0%B0%EB%84%88_zauzcb.png",
  },
  {
    eventid: 416,
    name: "ECHO BLOOM",
    category: "concert",
    start_time: new Date("2026-10-24T18:00:00"),
    end_time: new Date("2026-10-24T21:00:00"),
    location: "서울특별시 광진구 능동로 120",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718128/%EC%BD%98%EC%84%9C%ED%8A%B84_%EA%B8%B0%EB%B3%B8_kit9c3.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718067/%EC%BD%98%EC%84%9C%ED%8A%B84_%EB%B0%B0%EB%84%88_dhxsz3.png",
  },
];

const weeklyRankingIds = [413, 409, 405, 401, 414];

const weeklyRankingEvents = weeklyRankingIds
  .map((eventid) =>
    concertEvents.find((event) => event.eventid === eventid)
  )
  .filter(
    (event): event is EventPageItem => event !== undefined
  );

export default function ConcertPage() {
  return (
    <EventPageLayout
      categoryName="콘서트"
      events={concertEvents}
      weeklyRankingEvents={weeklyRankingEvents}
    />
  );
}