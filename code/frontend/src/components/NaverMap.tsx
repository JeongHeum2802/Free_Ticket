import { useEffect, useRef } from "react";

interface NaverMapProps {
  address: string;
}

export default function NaverMap({ address }: NaverMapProps) {
  const mapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!mapRef.current || !window.naver || !address) return;

    window.naver.maps.Service.geocode(
      { query: address },
      (status: any, response: any) => {
        if (status !== window.naver.maps.Service.Status.OK) {
          console.error("주소 검색에 실패했습니다.");
          return;
        }

        const result = response.v2.addresses[0];

        if (!result) {
          console.error("해당 주소의 위치를 찾을 수 없습니다.");
          return;
        }

        const position = new window.naver.maps.LatLng(
          Number(result.y),
          Number(result.x)
        );

        const map = new window.naver.maps.Map(mapRef.current, {
          center: position,
          zoom: 16,
        });

        new window.naver.maps.Marker({
          position,
          map,
        });
      }
    );
  }, [address]);

  return (
    <div
      ref={mapRef}
      style={{
        width: "100%",
        height: "360px",
      }}
    />
  );
}