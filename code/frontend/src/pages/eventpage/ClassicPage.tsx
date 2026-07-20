import SlidePosts from "../../components/SlidePosts";
import Barnner from "../../components/Banner";

import type { Concert } from "../../types/concert";

import { useState } from "react";

export default function ClassicPage() {
  const [concerts] = useState<Concert[]>([
    {
      id: 1,
      category: 'MUSICAL',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_58949.jpg/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260623/58949_big_main_s_58949.jpg/dims/quality/',
    },
    {
      id: 2,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202606/20260625/58962_big_main_58962.jpg/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202606/20260618/20260618-58962.jpg/dims/quality/70/',
    },
    {
      id: 3,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563_s.jpg/dims/quality/'
    },
    {
      id: 4,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202605/20260529/20260529-58354.jpg/dims/quality/'
    },
    {
      id: 5,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202604/20260414/20260414-58105.jpg/dims/quality/'
    },
    {
      id: 6,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202604/20260414/20260414-58103_2.jpg/dims/quality/'
    },
    {
      id: 7,
      category: 'CONCERT',
      imageUrl: 'https://tkfile.yes24.com/Upload2/Display/202605/20260528/58563_big_main_58563.png/dims/quality/',
      posterUrl: 'https://tkfile.yes24.com/upload2/perfblog/202605/20260515/20260515-58163.jpg/dims/quality/70/'
    }
  ]);
  return (
    <div>
      <SlidePosts />
      <Barnner title="Today's HOT" concerts={concerts}/>
      <Barnner title="마감 임박!" concerts={concerts}/>
    </div>
  )
}