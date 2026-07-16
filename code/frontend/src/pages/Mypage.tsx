import { useAuth } from "../context/AuthContext";


export default function Mypage() {
  const { user } = useAuth();

  return <div>{user && user.name}</div>;
}