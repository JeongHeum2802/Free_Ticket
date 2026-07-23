import { Link } from "react-router-dom";
import type { Concert } from "../types/Concert";

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
        <Link
          to={`/ticket/${concerts[0].id}`}
          className="row-span-2 col-span-2 overflow-hidden"
        >
          <img
            src={concerts[0].posterUrl}
            alt={`${concerts[0].category} 포스터`}
            className="w-full h-full object-cover transition-transform duration-300 hover:scale-105"
          />
        </Link>
        {
          subConcerts.map((concert) => (
            <Link
              key={concert.id}
              to={`/ticket/${concert.id}`}
              className="overflow-hidden"
            >
              <img
                src={concert.posterUrl}
                alt={`${concert.category} 포스터`}
                className="w-full h-full object-cover transition-transform duration-300 hover:scale-105"
              />
            </Link>
          ))
        }
      </div>
    </div>
  );
}

