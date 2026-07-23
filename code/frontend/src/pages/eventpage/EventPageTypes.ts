export type EventCategory =
  | "busking"
  | "musical"
  | "play"
  | "concert"
  | "exhibition"
  | "classic";

export type EventPageItem = {
  eventid: number;
  name: string;
  category: EventCategory;

  /*
    현재 더미데이터에서는 Date를 사용하지만,
    나중에 서버에서 문자열로 받을 가능성도 있어서 둘 다 허용합니다.
  */
  start_time: Date | string;
  end_time: Date | string;

  location: string;

  // 세로 포스터 이미지
  postUrl: string;

  // 가로 배너 이미지
  imageUrl: string;

  /*
    YES24 스타일의 '단독', '추천' 표시용입니다.
    실제 더미데이터에는 없어도 됩니다.
  */
  badge?: string;
};