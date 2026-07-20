import { useAuth } from "../../context/AuthContext";

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

          <input
            id="name"
            type="text"
            defaultValue={user?.name ?? ""}
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black"
          />
        </div>

        <div>
          <label
            htmlFor="email"
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            이메일
          </label>

          <input
            id="email"
            type="email"
            defaultValue={user?.email ?? ""}
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black"
          />
        </div>

        <div>
          <label
            htmlFor="phone"
            className="mb-2 block text-sm font-medium text-gray-700"
          >
            전화번호
          </label>

          <input
            id="phone"
            type="tel"
            placeholder="010-0000-0000"
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none focus:border-black"
          />
        </div>

        <button
          type="button"
          className="rounded-lg bg-black px-6 py-3 text-white transition-colors hover:bg-gray-800"
        >
          개인정보 수정
        </button>
      </div>
    </section>
  );
}