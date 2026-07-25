import { Routes, Route, Navigate, useLocation } from 'react-router-dom';

//// Pages

// 기본 페이지
import Homepage from './pages/Homepage';
import Loginpage from './pages/Loginpage';
import SearchResultsPage from './pages/SearchResultsPage';
import TicketDetailPage from './pages/TicketDetailPage';

// 마이페이지
import Mypage from './pages/mypage/Mypage';
import ReservedTickets from './pages/mypage/ReservedTickets';
import Profile from './pages/mypage/Profile';
import SellingTickets from './pages/mypage/SellingTickets';
import ProfileModify from './pages/mypage/ProfileModify';
import PasswordReset from './pages/mypage/PasswordReset';

// 장르별 티켓 페이지
import ConcertPage from './pages/eventpage/ConcertPage';
import MusicalPage from './pages/eventpage/MusicalPage';
import PlayPage from './pages/eventpage/PlayPage';
import ClassicPage from './pages/eventpage/ClassicPage';
import ExhibitionPage from './pages/eventpage/ExhibitionPage';
import BuskingPage from './pages/eventpage/BuskingPage';

//// Components
import EventSearchBar from './components/EventSearchBar';
import Header from './components/Header';
import ProtectedRoute from './components/ProtectedRoute';


export default function App() {
  const location = useLocation();
  const normalizedPath = location.pathname.replace(/\/+$/, "") || "/";
  const searchBarKey =
    normalizedPath === "/search"
      ? `${normalizedPath}${location.search}`
      : normalizedPath;

  return (
    <div>
      <Header />
      <EventSearchBar key={searchBarKey} />
      <Routes>
        <Route path="/" element={<Homepage />} />
        <Route path="/concert" element={<ConcertPage />} />
        <Route path="/musical" element={<MusicalPage />} />
        <Route path="/play" element={<PlayPage />} />
        <Route path="/classic" element={<ClassicPage />} />
        <Route path="/exhibition" element={<ExhibitionPage />} />
        <Route path="/busking" element={<BuskingPage />} />

        <Route path="/login" element={<Loginpage />} />
        <Route path="/search" element={<SearchResultsPage />} />
        <Route path="/ticket/:id" element={<TicketDetailPage />} />
        <Route
          path="/mypage"
          element={
            <ProtectedRoute>
              <Mypage />
            </ProtectedRoute>
          }>
          <Route index element={<Navigate to="tickets" replace />} />
          <Route path="tickets" element={<ReservedTickets />} />
          <Route path="profile" element={<Profile />} />
          <Route path="selling" element={<SellingTickets />} />
          <Route path="profileModify" element={<ProfileModify />} />
          <Route path="passwordreset" element={<PasswordReset />} />
        </Route>
      </Routes>
    </div>
  );
}
