import EventPageLayout from "./EventPageLayout";
import type { EventPageItem } from "./EventPageTypes";

const buskingEvents: EventPageItem[] = [
  {
    eventid: 601,
    name: "한강 노을 버스킹",
    category: "busking",
    start_time: new Date("2026-08-03T18:30:00"),
    end_time: new Date("2026-08-03T20:00:00"),
    location: "서울특별시 영등포구 여의동로 330",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615566/event-601-post_wfisid.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615566/event-601-banner_eir9m7.png",
  },
  {
    eventid: 602,
    name: "바다를 닮은 노래",
    category: "busking",
    start_time: new Date("2026-08-16T17:00:00"),
    end_time: new Date("2026-08-16T19:00:00"),
    location: "부산광역시 수영구 광안해변로 219",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615565/event-602-post_spi7b3.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615569/event-602-banner_cej98g.png",
  },
  {
    eventid: 603,
    name: "거리에서 만난 재즈",
    category: "busking",
    start_time: new Date("2026-09-12T18:00:00"),
    end_time: new Date("2026-09-12T20:00:00"),
    location: "서울특별시 마포구 어울마당로 94-12",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615566/event-603-post_xwtlkh.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615570/event-603-banner_cam36c.png",
  },
  {
    eventid: 604,
    name: "제주의 밤과 음악",
    category: "busking",
    start_time: new Date("2026-10-10T17:30:00"),
    end_time: new Date("2026-10-10T19:30:00"),
    location: "제주특별자치도 제주시 탑동해안로 74",
    postUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615567/event-604-post_i4rywx.png",
    imageUrl: "https://res.cloudinary.com/de0jdvkyy/image/upload/v1784615568/event-604-banner_djjc0x.png",
  },
  {
    eventid: 605,
    name: "골목길 어쿠스틱",
    category: "busking",
    start_time: new Date("2026-08-14T18:30:00"),
    end_time: new Date("2026-08-14T20:00:00"),
    location: "서울특별시 종로구 익선동 166-40",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715088/605-poster_hvr0im.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715195/605-banner_tocls0.png",
  },
  {
    eventid: 606,
    name: "노을빛 기타 소리",
    category: "busking",
    start_time: new Date("2026-09-05T17:30:00"),
    end_time: new Date("2026-09-05T19:00:00"),
    location: "강원특별자치도 강릉시 창해로 514",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715087/606-poster_bkffrr.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715195/606-banner_b4dzrx.png",
  },
  {
    eventid: 607,
    name: "한밤의 재즈 산책",
    category: "busking",
    start_time: new Date("2026-10-09T19:30:00"),
    end_time: new Date("2026-10-09T21:30:00"),
    location: "서울특별시 성동구 연무장길 81",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715087/607-poster_alhexq.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715194/607-banner_lyjlz4.png",
  },
  {
    eventid: 608,
    name: "섬마을 작은 음악회",
    category: "busking",
    start_time: new Date("2026-11-07T16:00:00"),
    end_time: new Date("2026-11-07T18:00:00"),
    location: "제주특별자치도 제주시 애월읍 애월해안로 656",
    postUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715087/608-poster_r2mo87.png",
    imageUrl: "https://res.cloudinary.com/wzxxzxfv/image/upload/v1784715194/608-banner_budsll.png",
  },
  {
    eventid: 609,
    name: "여의도 물빛 라이브",
    category: "busking",
    start_time: new Date("2026-08-15T18:00:00"),
    end_time: new Date("2026-08-15T20:00:00"),
    location: "서울특별시 영등포구 여의동로 330",
    postUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707135/%EB%B2%84%EC%8A%A4%ED%82%B91-2_wqwgld.png",
    imageUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707135/%EB%B2%84%EC%8A%A4%ED%82%B91-1_c3ajwj.png",
    badge: "단독",
  },
  {
    eventid: 610,
    name: "신촌 연세로 청춘 마이크",
    category: "busking",
    start_time: new Date("2026-08-21T17:00:00"),
    end_time: new Date("2026-08-21T19:30:00"),
    location: "서울특별시 서대문구 연세로 5",
    postUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707136/%EB%B2%84%EC%8A%A4%ED%82%B92-2_vigiso.png",
    imageUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707136/%EB%B2%84%EC%8A%A4%ED%82%B92-1_itfubr.png",
    badge: "추천",
  },
  {
    eventid: 611,
    name: "광안리 해변의 통기타",
    category: "busking",
    start_time: new Date("2026-09-04T18:30:00"),
    end_time: new Date("2026-09-04T20:30:00"),
    location: "부산광역시 수영구 광안해변로 219",
    postUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707136/%EB%B2%84%EC%8A%A4%ED%82%B93-2_estoa4.png",
    imageUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707136/%EB%B2%84%EC%8A%A4%ED%82%B93-1_zudrpb.png",
  },
  {
    eventid: 612,
    name: "청계천 가을밤 재즈",
    category: "busking",
    start_time: new Date("2026-09-25T19:00:00"),
    end_time: new Date("2026-09-25T21:00:00"),
    location: "서울특별시 중구 청계천로 1",
    postUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707136/%EB%B2%84%EC%8A%A4%ED%82%B94-2_p9i2ck.png",
    imageUrl:
      "https://res.cloudinary.com/kzayufbe/image/upload/v1784707136/%EB%B2%84%EC%8A%A4%ED%82%B94-1_dxaeob.png",
  },
  {
    eventid: 613,
    name: "강변의 기록",
    category: "busking",
    start_time: new Date("2026-08-14T18:00:00"),
    end_time: new Date("2026-08-14T20:00:00"),
    location: "서울특별시 마포구 어울마당로 65",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717915/%EB%B2%84%EC%8A%A4%ED%82%B91_%EA%B8%B0%EB%B3%B8_galbzx.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717861/%EB%B2%84%EC%8A%A4%ED%82%B91_%EB%B0%B0%EB%84%88_dnyn0r.png",
  },
  {
    eventid: 614,
    name: "리버사이드 버스킹 페스타",
    category: "busking",
    start_time: new Date("2026-08-22T17:00:00"),
    end_time: new Date("2026-08-22T19:30:00"),
    location: "서울특별시 영등포구 여의동로 330",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717913/%EB%B2%84%EC%8A%A4%ED%82%B92_%EA%B8%B0%EB%B3%B8_pwy6oh.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717858/%EB%B2%84%EC%8A%A4%ED%82%B92_%EB%B0%B0%EB%84%88_froses.png",
  },
  {
    eventid: 615,
    name: "홍대버스킹",
    category: "busking",
    start_time: new Date("2026-09-08T18:30:00"),
    end_time: new Date("2026-09-08T20:30:00"),
    location: "서울특별시 종로구 청계천로 1",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717911/%EB%B2%84%EC%8A%A4%ED%82%B93_%EA%B8%B0%EB%B3%B8_f8erjx.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717856/%EB%B2%84%EC%8A%A4%ED%82%B93_%EB%B0%B0%EB%84%88_jvvn4i.png",
  },
  {
    eventid: 616,
    name: "바다버스킹",
    category: "busking",
    start_time: new Date("2026-09-29T19:00:00"),
    end_time: new Date("2026-09-29T21:00:00"),
    location: "인천광역시 중구 월미문화로 35",
    postUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717911/%EB%B2%84%EC%8A%A4%ED%82%B94_%EA%B8%B0%EB%B3%B8_f2r5wc.png",
    imageUrl: "https://res.cloudinary.com/dek8tiywb/image/upload/v1784717857/%EB%B2%84%EC%8A%A4%ED%82%B94_%EB%B0%B0%EB%84%88_qqeh0v.png",
  }
];

const weeklyRankingIds = [
  614,
  602,
  605,
  613,
  603,
];

const weeklyRankingEvents = weeklyRankingIds
  .map((eventid) =>
    buskingEvents.find(
      (event) => event.eventid === eventid
    )
  )
  .filter(
    (event): event is EventPageItem =>
      event !== undefined
  );

export default function BuskingPage() {
  return (
    <EventPageLayout
      categoryName="버스킹"
      events={buskingEvents}
      weeklyRankingEvents={weeklyRankingEvents}
    />
  );
}

// import SlidePosts from "../../components/SlidePosts";
// import Barnner from "../../components/Banner";

// import type { Concert } from "../../types/concert";

// import { useState } from "react";

// export default function BuskingPage() {
//   const [concerts] = useState<Concert[]>([
//     {
//       id: 1,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_58949.jpg/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_s_58949.jpg/dims/quality/',
//     },
//     {
//       id: 2,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260625/58962_big_main_58962.jpg/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202606/20260618/20260618-58962.jpg/dims/quality/70/',
//     },
//     {
//       id: 3,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563_s.jpg/dims/quality/'
//     },
//     {
//       id: 4,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202605/20260529/20260529-58354.jpg/dims/quality/'
//     },
//     {
//       id: 5,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202604/20260414/20260414-58105.jpg/dims/quality/'
//     },
//     {
//       id: 6,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202604/20260414/20260414-58103_2.jpg/dims/quality/'
//     },
//     {
//       id: 7,
//       category: 'BUSKING',
//       imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
//       posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202605/20260515/20260515-58163.jpg/dims/quality/70/'
//     }
//   ]);
//   return (
//     <div>
//       <SlidePosts />
//       <Banner title="Today's HOT" concerts={concerts}/>
//       <Banner title="마감 임박!" concerts={concerts}/>
//     </div>
//   )
// }