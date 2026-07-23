import EventPageLayout from "./EventPageLayout";
import type { EventPageItem } from "./EventPageTypes";

const classicEvents: EventPageItem[] = [
  {
    eventid: 501,
    name: "한여름 밤의 교향곡",
    category: "classic",
    start_time: new Date("2026-08-09T17:00:00"),
    end_time: new Date("2026-08-09T19:00:00"),
    location: "서울특별시 서초구 남부순환로 2406",
    postUrl: "",
    imageUrl: "",
  },
  {
    eventid: 502,
    name: "피아노와 함께하는 여행",
    category: "classic",
    start_time: new Date("2026-08-27T19:30:00"),
    end_time: new Date("2026-08-27T21:30:00"),
    location: "경기도 수원시 팔달구 효원로307번길 20",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615562/event-501-post_hqkcog.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615562/event-501-banner_rrseba.png",
  },
  {
    eventid: 503,
    name: "가을을 여는 현악 사중주",
    category: "classic",
    start_time: new Date("2026-09-06T17:00:00"),
    end_time: new Date("2026-09-06T19:00:00"),
    location: "대구광역시 수성구 무학로 180",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615563/event-503-post_eabff2.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615564/event-503-banner_yj3okc.png",
  },
  {
    eventid: 504,
    name: "베토벤의 밤",
    category: "classic",
    start_time: new Date("2026-10-24T19:00:00"),
    end_time: new Date("2026-10-24T21:00:00"),
    location: "대전광역시 서구 둔산대로 135",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615564/event-504-post_mw2cv5.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615564/event-504-banner_dhwxbq.png",
  },
  {
    eventid: 505,
    name: "달빛 피아노 리사이틀",
    category: "classic",
    start_time: new Date("2026-08-23T17:00:00"),
    end_time: new Date("2026-08-23T19:00:00"),
    location: "서울특별시 영등포구 국회대로 596",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/505-poster_sieivc.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715196/505-banner_qa98in.png",
  },
  {
    eventid: 506,
    name: "바람의 선율",
    category: "classic",
    start_time: new Date("2026-09-27T16:30:00"),
    end_time: new Date("2026-09-27T18:30:00"),
    location: "경기도 용인시 수지구 포은대로 499",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715089/506-poster_mb5tvz.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715195/506-banner_vu3dyg.png",
  },
  {
    eventid: 507,
    name: "모차르트와의 오후",
    category: "classic",
    start_time: new Date("2026-10-18T15:00:00"),
    end_time: new Date("2026-10-18T17:00:00"),
    location: "광주광역시 서구 내방로 111",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715088/507-poster_uoibs9.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715195/507-banner_dcmhuj.png",
  },
  {
    eventid: 508,
    name: "겨울의 첼로",
    category: "classic",
    start_time: new Date("2026-11-22T17:30:00"),
    end_time: new Date("2026-11-22T19:30:00"),
    location: "대전광역시 서구 둔산대로 135",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715088/508-poster_oya4oe.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715195/508-banner_g5good.png",
  },
  {
    eventid: 509,
    name: "가을밤의 슈베르트",
    category: "classic",
    start_time: new Date("2026-09-10T19:30:00"),
    end_time: new Date("2026-09-10T21:30:00"),
    location: "서울특별시 송파구 올림픽로 300",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707139/%ED%81%B4%EB%9E%98%EC%8B%9D1-2_n7xqyj.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707139/%ED%81%B4%EB%9E%98%EC%8B%9D1-1_exxpc4.png",
  },
  {
    eventid: 510,
    name: "로맨틱 첼로 선율 속으로",
    category: "classic",
    start_time: new Date("2026-08-28T20:00:00"),
    end_time: new Date("2026-08-28T21:40:00"),
    location: "서울특별시 서초구 남부순환로 2406",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707140/%ED%81%B4%EB%9E%98%EC%8B%9D2-2_cced5h.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707139/%ED%81%B4%EB%9E%98%EC%8B%9D2-1_eawpm6.png",
  },
  {
    eventid: 511,
    name: "모차르트와 거장들",
    category: "classic",
    start_time: new Date("2026-10-09T17:00:00"),
    end_time: new Date("2026-10-09T19:00:00"),
    location: "서울특별시 종로구 세종대로 175",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707140/%ED%81%B4%EB%9E%98%EC%8B%9D3-2_odlanu.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707140/%ED%81%B4%EB%9E%98%EC%8B%9D3-1_hbct85.png",
  },
  {
    eventid: 512,
    name: "건반 위의 시, 피아노 리사이틀",
    category: "classic",
    start_time: new Date("2026-11-05T19:30:00"),
    end_time: new Date("2026-11-05T21:30:00"),
    location: "서울특별시 서대문구 연세로 50",
    postUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707140/%ED%81%B4%EB%9E%98%EC%8B%9D4-2_xpu3z6.png",
    imageUrl: "https://res.cloudinary.com/kzayufbe/image/upload/v1784707140/%ED%81%B4%EB%9E%98%EC%8B%9D4-1_lsxxhs.png",
  },
  {
    eventid: 513,
    name: "루미너스 클래식",
    category: "classic",
    start_time: new Date("2026-09-12T19:30:00"),
    end_time: new Date("2026-09-12T21:30:00"),
    location: "서울특별시 종로구 세종대로 175",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718196/%ED%81%B4%EB%9E%98%EC%8B%9D1_%EA%B8%B0%EB%B3%B8_ixfrib.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718167/%ED%81%B4%EB%9E%98%EC%8B%9D1_%EB%B0%B0%EB%84%88_ws8rvt.jpg",
  },
  {
    eventid: 514,
    name: "달빛의 서곡",
    category: "classic",
    start_time: new Date("2026-09-20T17:00:00"),
    end_time: new Date("2026-09-20T19:00:00"),
    location: "서울특별시 서초구 반포대로 211",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718194/%ED%81%B4%EB%9E%98%EC%8B%9D2_%EA%B8%B0%EB%B3%B8_vuwuhv.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718170/%ED%81%B4%EB%9E%98%EC%8B%9D2_%EB%B0%B0%EB%84%88_u7utfk.jpg",
  },
  {
    eventid: 515,
    name: "비르투오조의 밤을 수놓다",
    category: "classic",
    start_time: new Date("2026-10-15T19:30:00"),
    end_time: new Date("2026-10-15T21:30:00"),
    location: "서울특별시 송파구 올림픽로 300",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718191/%ED%81%B4%EB%9E%98%EC%8B%9D3_%EA%B8%B0%EB%B3%B8_iv8su1.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718175/%ED%81%B4%EB%9E%98%EC%8B%9D3_%EB%B0%B0%EB%84%88_nwjgee.jpg",
  },
  {
    eventid: 516,
    name: "썸머 클래식 앙상블",
    category: "classic",
    start_time: new Date("2026-11-10T20:00:00"),
    end_time: new Date("2026-11-10T22:00:00"),
    location: "서울특별시 마포구 어울마당로 35",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718188/%ED%81%B4%EB%9E%98%EC%8B%9D4_%EA%B8%B0%EB%B3%B8_kusg34.jpg",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784718172/%ED%81%B4%EB%9E%98%EC%8B%9D4_%EB%B0%B0%EB%84%88_c0l8zi.jpg",
  },
];

const weeklyRankingIds = [513, 509, 505, 503, 514];

const weeklyRankingEvents = weeklyRankingIds
  .map((eventid) =>
    classicEvents.find((event) => event.eventid === eventid)
  )
  .filter(
    (event): event is EventPageItem => event !== undefined
  );

export default function ClassicPage() {
  return (
    <EventPageLayout
      categoryName="클래식/무용"
      events={classicEvents}
      weeklyRankingEvents={weeklyRankingEvents}
    />
  );
}