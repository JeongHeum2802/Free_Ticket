import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

export type SlidePostItem = {
  id: number;
  name: string;
  imageUrl: string;
  posterUrl: string;
};

type SlidePostsProps = {
  slides: SlidePostItem[];
};

export default function SlidePosts({ slides }: SlidePostsProps) {
  const validSlides = slides.filter(
    (slide) => slide.imageUrl.trim() && slide.posterUrl.trim(),
  );
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  useEffect(() => {
    if (validSlides.length <= 1) {
      return;
    }

    const timer = window.setTimeout(() => {
      setCurrentIndex((previousIndex) => (previousIndex + 1) % validSlides.length);
    }, 7000);

    return () => window.clearTimeout(timer);
  }, [currentIndex, validSlides.length]);

  if (validSlides.length === 0) {
    return null;
  }

  const safeCurrentIndex = currentIndex % validSlides.length;

  return (
    <section className="relative flex h-150 w-full items-center justify-center overflow-hidden bg-[#280f0f] text-white">
      {validSlides.map((slide, index) => (
        <Link
          key={slide.id}
          to={`/ticket/${slide.id}`}
          aria-label={`${slide.name} 상세보기`}
          className={`absolute inset-0 bg-cover bg-center transition-opacity duration-1000 ${
            index === safeCurrentIndex
              ? "pointer-events-auto opacity-100"
              : "pointer-events-none opacity-0"
          }`}
          style={{ backgroundImage: `url(${slide.imageUrl})` }}
        />
      ))}

      {validSlides.length > 1 && (
        <>
          <button
            type="button"
            onClick={() =>
              setCurrentIndex((index) =>
                index === 0 ? validSlides.length - 1 : index - 1,
              )
            }
            aria-label="이전 슬라이드"
            className="absolute left-8 z-30 text-gray-400 transition-colors hover:text-white"
          >
            <span className="text-4xl" aria-hidden="true">‹</span>
          </button>
          <button
            type="button"
            onClick={() =>
              setCurrentIndex((index) => (index + 1) % validSlides.length)
            }
            aria-label="다음 슬라이드"
            className="absolute right-8 z-30 text-gray-400 transition-colors hover:text-white"
          >
            <span className="text-4xl" aria-hidden="true">›</span>
          </button>
        </>
      )}

      <div
        className={`absolute bottom-8 z-20 w-full px-10 transition-opacity ${
          isMenuOpen ? "pointer-events-none opacity-0" : "opacity-100"
        }`}
      >
        <div className="mx-auto flex h-0.5 w-full max-w-200 bg-gray-600">
          {validSlides.map((slide, index) => (
            <button
              key={slide.id}
              type="button"
              onClick={() => setCurrentIndex(index)}
              aria-label={`${index + 1}번째 슬라이드`}
              className={`h-full flex-1 ${
                index === safeCurrentIndex ? "bg-[#ff4b2b]" : "bg-transparent"
              }`}
            />
          ))}
        </div>
      </div>

      <div
        onMouseEnter={() => setIsMenuOpen(true)}
        className="absolute bottom-0 z-40 h-28 w-full"
      />

      <div
        onMouseEnter={() => setIsMenuOpen(true)}
        onMouseLeave={() => setIsMenuOpen(false)}
        className={`absolute bottom-0 z-50 w-full border-t border-white/10 bg-black/70 px-10 pb-6 pt-5 backdrop-blur-sm transition-all duration-500 ${
          isMenuOpen
            ? "translate-y-0 opacity-100"
            : "pointer-events-none translate-y-full opacity-0"
        }`}
      >
        <div className="mb-5 flex justify-center gap-3">
          {validSlides.map((slide, index) => (
            <button
              key={slide.id}
              type="button"
              onClick={() => setCurrentIndex(index)}
              aria-label={`${slide.name} 슬라이드 선택`}
              className={`h-28 w-20 overflow-hidden border-2 transition-all ${
                index === safeCurrentIndex
                  ? "scale-105 border-orange-400 opacity-100"
                  : "border-transparent opacity-60 hover:opacity-100"
              }`}
            >
              <img
                src={slide.posterUrl}
                alt={`${slide.name} 포스터`}
                className="h-full w-full object-cover"
              />
            </button>
          ))}
        </div>
        <p className="text-center text-xs font-bold text-gray-300">
          {safeCurrentIndex + 1} / {validSlides.length}
        </p>
      </div>
    </section>
  );
}
