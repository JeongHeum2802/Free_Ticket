import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

/*
  SlidePosts가 화면을 그리는 데 필요한
  최소한의 슬라이드 데이터 타입입니다.
*/
export type SlidePostItem = {
  id: number;
  name: string;
  imageUrl: string;
  posterUrl: string;
};

type SlidePostsProps = {
  /*
    이벤트 페이지에서는 카테고리별 슬라이드를 전달합니다.

    Homepage처럼 slides를 전달하지 않는 곳에서는
    아래 defaultSlides를 사용합니다.
  */
  slides?: SlidePostItem[];
};

/*
  기존 홈페이지에서 보여주던 기본 슬라이드입니다.

  Homepage에서는 여전히 <SlidePosts />만 사용하므로
  기존 배너가 그대로 나옵니다.
*/
const defaultSlides: SlidePostItem[] = [
  {
    id: 1,
    name: "뮤지컬 추천",
    imageUrl:
      "https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_58949.jpg/dims/quality/70/",
    posterUrl:
      "https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_s_58949.jpg/dims/quality/70/",
  },
  {
    id: 2,
    name: "콘서트 추천",
    imageUrl:
      "https://tkfile.yes24.com/Upload2/Display/202606/20260625/58962_big_main_58962.jpg/dims/quality/70/",
    posterUrl:
      "https://tkfile.yes24.com/Upload2/Display/202606/20260625/58962_big_main_s_58962.jpg/dims/quality/70/",
  },
  {
    id: 3,
    name: "콘서트 추천",
    imageUrl:
      "https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/70/",
    posterUrl:
      "https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563_s.jpg/dims/quality/70/",
  },
];

export default function SlidePosts({
  slides = defaultSlides,
}: SlidePostsProps) {
  /*
    이미지 URL이 비어 있는 데이터는 슬라이드에서 제외합니다.

    현재 201, 301, 501번처럼
    이미지가 비어 있는 이벤트가 있어도 깨진 배너가 나오지 않습니다.
  */
  const validSlides = slides.filter(
    (slide) =>
      slide.imageUrl.trim() !== "" &&
      slide.posterUrl.trim() !== ""
  );

  const [currentIndex, setCurrentIndex] =
    useState(0);

  const [isMenuOpen, setIsMenuOpen] =
    useState(false);

  /*
    현재 슬라이드가 바뀐 뒤 7초가 지나면
    다음 슬라이드로 이동합니다.

    버튼을 눌러 currentIndex가 변경되면
    타이머도 다시 7초부터 시작합니다.
  */
  useEffect(() => {
    if (validSlides.length <= 1) {
      return;
    }

    const timer = window.setTimeout(() => {
      setCurrentIndex(
        (previousIndex) =>
          (previousIndex + 1) %
          validSlides.length
      );
    }, 7000);

    return () => {
      window.clearTimeout(timer);
    };
  }, [currentIndex, validSlides.length]);

  /*
    표시할 수 있는 이미지가 하나도 없으면
    슬라이드 영역을 만들지 않습니다.
  */
  if (validSlides.length === 0) {
    return null;
  }

  /*
    데이터 개수가 바뀌어도
    currentIndex가 배열 범위를 벗어나지 않도록 합니다.
  */
  const safeCurrentIndex =
    currentIndex % validSlides.length;

  const handlePrev = () => {
    setCurrentIndex((previousIndex) =>
      previousIndex === 0
        ? validSlides.length - 1
        : previousIndex - 1
    );
  };

  const handleNext = () => {
    setCurrentIndex(
      (previousIndex) =>
        (previousIndex + 1) %
        validSlides.length
    );
  };

  const handleSelectSlide = (
    index: number
  ) => {
    setCurrentIndex(index);
  };

  return (
    <div className="relative flex h-150 w-full items-center justify-center overflow-hidden bg-[#280f0f] text-white">
      {/* 큰 배너 이미지 */}
      {validSlides.map((slide, index) => (
        <Link
          key={slide.id}
          to={`/ticket/${slide.id}`}
          aria-label={`${slide.name} 상세보기`}
          className={`
            absolute inset-0 z-0
            bg-cover bg-center
            transition-opacity
            duration-1000
            ease-in-out
            ${
              index === safeCurrentIndex
                ? "pointer-events-auto opacity-100"
                : "pointer-events-none opacity-0"
            }
          `}
          style={{
            backgroundImage: `url(${slide.imageUrl})`,
          }}
        />
      ))}

      {/* 슬라이드가 2개 이상일 때만 이동 버튼 표시 */}
      {validSlides.length > 1 && (
        <>
          {/* 이전 버튼 */}
          <button
            type="button"
            onClick={handlePrev}
            aria-label="이전 슬라이드"
            className="
              absolute left-8 z-30
              text-gray-400
              transition-colors
              hover:text-white
            "
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
              className="h-10 w-10"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M15.75 19.5L8.25 12l7.5-7.5"
              />
            </svg>
          </button>

          {/* 다음 버튼 */}
          <button
            type="button"
            onClick={handleNext}
            aria-label="다음 슬라이드"
            className="
              absolute right-8 z-30
              text-gray-400
              transition-colors
              hover:text-white
            "
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
              className="h-10 w-10"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M8.25 4.5l7.5 7.5-7.5 7.5"
              />
            </svg>
          </button>
        </>
      )}

      {/* 기본 하단 프로그레스 바 */}
      <div
        className={`
          absolute bottom-8 left-0 z-20
          w-full px-10
          transition-opacity
          duration-300
          ${
            isMenuOpen
              ? "pointer-events-none opacity-0"
              : "opacity-100"
          }
        `}
      >
        <div className="mx-auto flex h-0.5 w-full max-w-200 bg-gray-600">
          {validSlides.map(
            (slide, index) => (
              <button
                key={slide.id}
                type="button"
                onClick={() =>
                  handleSelectSlide(index)
                }
                aria-label={`${index + 1}번째 슬라이드`}
                className={`
                  h-full flex-1
                  transition-all
                  duration-300
                  ${
                    index === safeCurrentIndex
                      ? "bg-[#ff4b2b]"
                      : "bg-transparent"
                  }
                `}
              />
            )
          )}
        </div>
      </div>

      {/* 하단 메뉴를 여는 마우스 감지 영역 */}
      <div
        onMouseEnter={() =>
          setIsMenuOpen(true)
        }
        className="
          absolute bottom-0 left-0
          z-40 h-28 w-full
        "
      />

      {/* 하단 포스터 메뉴 */}
      <div
        onMouseEnter={() =>
          setIsMenuOpen(true)
        }
        onMouseLeave={() =>
          setIsMenuOpen(false)
        }
        className={`
          absolute bottom-0 left-0
          z-50 w-full
          border-t border-white/10
          bg-black/70
          px-10 pb-6 pt-5
          backdrop-blur-sm
          transition-all
          duration-500
          ease-out
          ${
            isMenuOpen
              ? "translate-y-0 opacity-100"
              : "pointer-events-none translate-y-full opacity-0"
          }
        `}
      >
        {/* 작은 포스터 목록 */}
        <div className="mb-5 flex justify-center gap-3">
          {validSlides.map(
            (slide, index) => (
              <button
                key={slide.id}
                type="button"
                onClick={() =>
                  handleSelectSlide(index)
                }
                aria-label={`${slide.name} 슬라이드 선택`}
                className={`
                  h-28 w-20
                  overflow-hidden
                  border-2
                  transition-all
                  duration-300
                  ${
                    index === safeCurrentIndex
                      ? "scale-105 border-orange-400 opacity-100"
                      : "border-transparent opacity-60 hover:scale-105 hover:opacity-100"
                  }
                `}
              >
                <img
                  src={slide.posterUrl}
                  alt={`${slide.name} 포스터`}
                  className="
                    h-full w-full
                    object-cover
                  "
                />
              </button>
            )
          )}
        </div>

        {/* 현재 슬라이드 번호 */}
        <div className="mb-2 text-center text-xs font-bold text-gray-300">
          {safeCurrentIndex + 1} /{" "}
          {validSlides.length}
        </div>

        {/* 메뉴 안 프로그레스 바 */}
        <div className="mx-auto flex h-0.5 w-full max-w-200 bg-gray-600">
          {validSlides.map(
            (slide, index) => (
              <button
                key={slide.id}
                type="button"
                onClick={() =>
                  handleSelectSlide(index)
                }
                aria-label={`${index + 1}번째 슬라이드`}
                className={`
                  h-full flex-1
                  transition-all
                  duration-300
                  ${
                    index === safeCurrentIndex
                      ? "bg-[#ff4b2b]"
                      : "bg-transparent"
                  }
                `}
              />
            )
          )}
        </div>
      </div>
    </div>
  );
}