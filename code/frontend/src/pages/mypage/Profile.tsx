import { useAuth } from "../../context/AuthContext";
import { Link } from "react-router-dom";

export default function Profile() {
  const { user } = useAuth();

  return (
    <section>
      <h1 className="text-2xl font-bold">개인정보</h1>
      <p className="mt-2 text-sm text-gray-500">
        회원 정보를 확인하고 수정할 수 있습니다.
      </p>

      <div className="mt-6 max-w-xl space-y-5">
        <div>
          <label
            htmlFor="name"
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            이름
          </label>

          <div className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black">
            {user?.username}
          </div>
        </div>

        <div>
          <label
            htmlFor="email"
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            이메일
          </label>

          <div className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black">
            {user?.email}
          </div>
        </div>

        <div>
          <label
            htmlFor="phone"
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            전화번호
          </label>

          <div className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black">
            {user?.phonenumber}
            </div>
        </div>

        <Link
          to="../profileModify"
          className="rounded-lg bg-black px-6 py-3 text-white transition-colors hover:bg-gray-800"
        >
          개인정보 수정
        </Link>
      </div>
    </section>
  );
}