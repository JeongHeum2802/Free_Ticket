import { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import type { Concert } from '../types/Concert';

export default function SlidePosts() {
  const [concerts] = useState<Concert[]>([
    {
      id: 1,
      category: 'MUSICAL',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_58949.jpg/dims/quality/70/',
      posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_s_58949.jpg/dims/quality/70/',
    },
    {
      id: 2,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260625/58962_big_main_58962.jpg/dims/quality/70/',
      posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260625/58962_big_main_s_58962.jpg/dims/quality/70/',
    },
    {
      id: 3,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/70/',
      posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563_s.jpg/dims/quality/70/'
    }
  ]);

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const timerRef = useRef<number | null>(null);

  const resetAutoSlide = () => {
    if (timerRef.current !== null) {
      clearInterval(timerRef.current);
    }

    timerRef.current = setInterval(() => {
      setCurrentIndex((prevIndex) => (prevIndex + 1) % concerts.length);
    }, 7000);
  };

  useEffect(() => {
    resetAutoSlide();

    return () => {
      if (timerRef.current !== null)
        clearInterval(timerRef.current);
    };
  }, [concerts.length]);

  const handlePrev = () => {
    setCurrentIndex((prevIndex) =>
      prevIndex === 0 ? concerts.length - 1 : prevIndex - 1
    );
    resetAutoSlide();
  };

  const handleNext = () => {
    setCurrentIndex((prevIndex) => (prevIndex + 1) % concerts.length);
    resetAutoSlide();
  };

  const handleSelectSlide = (index: number) => {
    setCurrentIndex(index);
    resetAutoSlide();
  };

  return (
    <div className="relative w-full h-150 bg-[#280f0f] text-white flex items-center justify-center overflow-hidden">
      {/* 배경 이미지 */}
      {concerts.map((slide, index) => (
        <Link
          to={`/ticket/${slide.id}`}
        >
          <div
            key={slide.id}
            className={`absolute inset-0 z-0 bg-cover bg-center transition-opacity duration-1000 ease-in-out ${index === currentIndex ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
              }`}
            style={{
              backgroundImage: slide.imageUrl
                ? `url(${slide.imageUrl})`
                : 'none',
            }}
          />
        </Link>
      ))}

      {/* 좌측 이동 버튼 */}
      <button
        onClick={handlePrev}
        className="absolute left-8 z-30 text-gray-400 hover:text-white transition-colors"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2}
          stroke="currentColor"
          className="w-10 h-10"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15.75 19.5L8.25 12l7.5-7.5"
          />
        </svg>
      </button>

      {/* 우측 이동 버튼 */}
      <button
        onClick={handleNext}
        className="absolute right-8 z-30 text-gray-400 hover:text-white transition-colors"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2}
          stroke="currentColor"
          className="w-10 h-10"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M8.25 4.5l7.5 7.5-7.5 7.5"
          />
        </svg>
      </button>

      {/* 기본 하단 프로그레스 바 */}
      <div
        className={`
          absolute bottom-8 left-0 z-20 w-full px-10
          transition-opacity duration-300
          ${isMenuOpen ? 'opacity-0 pointer-events-none' : 'opacity-100'}
        `}
      >
        <div className="w-full max-w-200 mx-auto h-0.5 bg-gray-600 flex">
          {concerts.map((_, index) => (
            <button
              key={index}
              onClick={() => handleSelectSlide(index)}
              className={`h-full flex-1 transition-all duration-300 ${index === currentIndex ? 'bg-[#ff4b2b]' : 'bg-transparent'
                }`}
            />
          ))}
        </div>
      </div>

      {/* 하단 마우스 감지 영역 */}
      <div
        onMouseEnter={() => setIsMenuOpen(true)}
        className="absolute bottom-0 left-0 z-40 w-full h-28"
      />

      {/* 하단 슬라이드 메뉴 */}
      <div
        onMouseEnter={() => setIsMenuOpen(true)}
        onMouseLeave={() => setIsMenuOpen(false)}
        className={`
          absolute bottom-0 left-0 z-50 w-full
          bg-black/70 backdrop-blur-sm
          border-t border-white/10
          px-10 pt-5 pb-6
          transition-all duration-500 ease-out
          ${isMenuOpen
            ? 'translate-y-0 opacity-100'
            : 'translate-y-full opacity-0 pointer-events-none'
          }
        `}
      >
        {/* 포스터 목록 */}
        <div className="flex justify-center gap-3 mb-5">
          {concerts.map((slide, index) => (
            <button
              key={slide.id}
              onClick={() => handleSelectSlide(index)}
              className={`
                w-20 h-28 overflow-hidden
                border-2 transition-all duration-300
                ${index === currentIndex
                  ? 'border-orange-400 scale-105 opacity-100'
                  : 'border-transparent opacity-60 hover:opacity-100 hover:scale-105'
                }
              `}
            >
              <img
                src={slide.posterUrl}
                className="w-full h-full object-cover"
              />
            </button>
          ))}
        </div>

        {/* 현재 슬라이드 번호 */}
        <div className="text-xs font-bold text-gray-300 text-center mb-2">
          {currentIndex + 1} / {concerts.length}
        </div>

        {/* 메뉴 안 프로그레스 바 */}
        <div className="w-full max-w-200 mx-auto h-0.5 bg-gray-600 flex">
          {concerts.map((_, index) => (
            <button
              key={index}
              onClick={() => handleSelectSlide(index)}
              className={`h-full flex-1 transition-all duration-300 ${index === currentIndex ? 'bg-[#ff4b2b]' : 'bg-transparent'
                }`}
            />
          ))}
        </div>
      </div>
    </div>
  );
}