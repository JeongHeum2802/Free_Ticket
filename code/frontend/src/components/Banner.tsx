
import type { Concert } from "../types/concert";

type Props = {
  title?: string;
  concerts: Concert[];
};

export default function Barnner({ title, concerts }: Props) {
  
  const subConcerts = concerts.slice(1);

  return (
    <div className="p-7">
      <header className="flex h-38 items-center justify-center text-[35px] font-bold text-[#333]">
        {title}
      </header>

      <div className="p-7 gap-4 grid grid-cols-5 gird-rows-2">
        <img
          src={concerts[0].posterUrl}
          className="w-full h-full row-span-2 col-span-2"
        />
        {
          subConcerts.map((concert) => (
            <img
              key={concert.id}
              src={concert.posterUrl}
              className="w-full h-full"
            />
          ))
        }
      </div>
    </div>
  );
}

