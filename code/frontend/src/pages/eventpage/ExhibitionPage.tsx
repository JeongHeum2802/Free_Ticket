import EventPageLayout from "./EventPageLayout";
import type { EventPageItem } from "./EventPageTypes";

const exhibitionEvents: EventPageItem[] = [
  {
    eventid: 301,
    name: "빛과 그림자의 경계",
    category: "exhibition",
    start_time: new Date("2026-08-04T10:00:00"),
    end_time: new Date("2026-08-30T18:00:00"),
    location: "서울특별시 용산구 서빙고로 137",
    postUrl: "",
    imageUrl: "",
  },
  {
    eventid: 302,
    name: "도시의 숨결",
    category: "exhibition",
    start_time: new Date("2026-08-15T10:30:00"),
    end_time: new Date("2026-09-20T19:00:00"),
    location: "서울특별시 중구 을지로 281",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615557/event-301-post_qbyrgf.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615556/event-301-banner_l7dpy0.png",
  },
  {
    eventid: 303,
    name: "한국 현대미술의 오늘",
    category: "exhibition",
    start_time: new Date("2026-09-01T10:00:00"),
    end_time: new Date("2026-10-11T18:00:00"),
    location: "부산광역시 해운대구 APEC로 58",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615558/event-303-post_ucbxkm.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615558/event-303-banner_fjuay1.png",
  },
  {
    eventid: 304,
    name: "자연을 담은 색채",
    category: "exhibition",
    start_time: new Date("2026-10-05T09:30:00"),
    end_time: new Date("2026-11-08T18:30:00"),
    location: "제주특별자치도 제주시 한림읍 용금로 883-5",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615558/event-304-post_fez34a.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615561/event-304-banner_mka8zt.png",
  },
  {
    eventid: 305,
    name: "기억의 조각들",
    category: "exhibition",
    start_time: new Date("2026-08-18T11:00:00"),
    end_time: new Date("2026-08-18T13:00:00"),
    location: "서울특별시 종로구 삼청로 30",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/305-poster_iic7pu.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715198/305-banner_blr4wo.png",
  },
  {
    eventid: 306,
    name: "미래 도시의 표정",
    category: "exhibition",
    start_time: new Date("2026-09-24T14:00:00"),
    end_time: new Date("2026-09-24T16:30:00"),
    location: "서울특별시 중구 동호로 257",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/306-poster_l1rhzi.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715198/306-banner_wpetil.png",
  },
  {
    eventid: 307,
    name: "바다와 인간",
    category: "exhibition",
    start_time: new Date("2026-10-21T10:30:00"),
    end_time: new Date("2026-10-21T12:30:00"),
    location: "부산광역시 해운대구 APEC로 55",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715088/307-poster_vmubyl.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715198/307-banner_o954iy.png",
  },
  {
    eventid: 308,
    name: "종이 위의 우주",
    category: "exhibition",
    start_time: new Date("2026-11-18T13:00:00"),
    end_time: new Date("2026-11-18T15:00:00"),
    location: "경기도 고양시 일산동구 호수로 595",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715088/308-poster_zn5u8d.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715197/308-banner_l3nmft.png",
  },
  {
    eventid: 309,
    name: "일상, 예술로 물들다",
    category: "exhibition",
    start_time: new Date("2026-08-15T10:00:00"),
    end_time: new Date("2026-11-15T18:00:00"),
    location: "서울특별시 서초구 남부순환로 2406",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784708085/%EC%A0%84%EC%8B%9C1-2_qysbnt.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784708082/%EC%A0%84%EC%8B%9C1-1_wdvhrj.png",
  },
  {
    eventid: 310,
    name: "미래 도시 풍경전",
    category: "exhibition",
    start_time: new Date("2026-09-01T10:00:00"),
    end_time: new Date("2026-12-01T20:00:00"),
    location: "서울특별시 중구 을지로 281",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707137/%EC%A0%84%EC%8B%9C2-2_j4w1b4.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707137/%EC%A0%84%EC%8B%9C2-1_oyujxq.png",
  },
  {
    eventid: 311,
    name: "자연과 인간, 그 연결고리",
    category: "exhibition",
    start_time: new Date("2026-08-10T10:00:00"),
    end_time: new Date("2026-10-31T18:00:00"),
    location: "서울특별시 종로구 삼청로 30",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707137/%EC%A0%84%EC%8B%9C3-2_ohjy8k.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707137/%EC%A0%84%EC%8B%9C3-1_trp5eh.png",
  },
  {
    eventid: 312,
    name: "시간의 조각들",
    category: "exhibition",
    start_time: new Date("2026-10-05T10:00:00"),
    end_time: new Date("2026-12-31T18:00:00"),
    location: "서울특별시 중구 덕수궁길 61",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707138/%EC%A0%84%EC%8B%9C4-2_gnqycf.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707137/%EC%A0%84%EC%8B%9C4-1_yz9ly7.png",
  },
  {
    eventid: 313,
    name: "시간의 결을 걷다",
    category: "exhibition",
    start_time: new Date("2026-08-20T10:00:00"),
    end_time: new Date("2026-11-20T18:00:00"),
    location: "서울특별시 중구 삼일대로 343",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718031/%EC%A0%84%EC%8B%9C%ED%9A%8C1_%EA%B8%B0%EB%B3%B8_kcnbh2.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718003/%EC%A0%84%EC%8B%9C%ED%9A%8C1_%EB%B0%B0%EB%84%88_rgtb7c.jpg",
  },
  {
    eventid: 314,
    name: "숨결의 정원",
    category: "exhibition",
    start_time: new Date("2026-09-05T10:00:00"),
    end_time: new Date("2026-12-05T19:00:00"),
    location: "서울특별시 서초구 남부순환로 2406",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718029/%EC%A0%84%EC%8B%9C%ED%9A%8C2_%EA%B8%B0%EB%B3%B8_t35zvo.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718007/%EC%A0%84%EC%8B%9C%ED%9A%8C2_%EB%B0%B0%EB%84%88_tpw9gg.png",
  },
  {
    eventid: 315,
    name: "비욘드 라이트",
    category: "exhibition",
    start_time: new Date("2026-09-15T10:00:00"),
    end_time: new Date("2026-11-30T18:00:00"),
    location: "서울특별시 종로구 효자로 12",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718025/%EC%A0%84%EC%8B%9C%ED%9A%8C3_%EA%B8%B0%EB%B3%B8_sgdlcb.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718003/%EC%A0%84%EC%8B%9C%ED%9A%8C3_%EB%B0%B0%EB%84%88_k4ixso.png",
  },
  {
    eventid: 316,
    name: "별의 정원",
    category: "exhibition",
    start_time: new Date("2026-10-10T10:00:00"),
    end_time: new Date("2026-12-31T18:00:00"),
    location: "서울특별시 마포구 홍익로 3",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718027/%EC%A0%84%EC%8B%9C%ED%9A%8C4_%EA%B8%B0%EB%B3%B8_ir7zyq.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718005/%EC%A0%84%EC%8B%9C%ED%9A%8C4_%EB%B2%A0%EB%84%88_dhjtgd.png",
  },
];

const weeklyRankingIds = [313, 309, 305, 303, 315];

const weeklyRankingEvents = weeklyRankingIds
  .map((eventid) =>
    exhibitionEvents.find((event) => event.eventid === eventid)
  )
  .filter(
    (event): event is EventPageItem => event !== undefined
  );

export default function ExhibitionPage() {
  return (
    <EventPageLayout
      categoryName="전시/스포츠"
      events={exhibitionEvents}
      weeklyRankingEvents={weeklyRankingEvents}
    />
  );
}