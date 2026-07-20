import { Routes, Route, Navigate } from 'react-router-dom';

// Pages
import Homepage from './pages/Homepage';
import Loginpage from './pages/Loginpage';
import Mypage from './pages/mypage/Mypage';
import ReservedTickets from './pages/mypage/ReservedTickets';
import Profile from './pages/mypage/Profile';
import SellingTickets from './pages/mypage/SellingTickets';

// Components
import Header from './components/Header';
import ProtectedRoute from './components/ProtectedRoute';

export default function App() {
  return (
    <div>
      <Header />
      <Routes>
        <Route path="/" element={<Homepage />} />
        <Route path="/login" element={<Loginpage />} />
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
        </Route>
      </Routes>
    </div>
  );
}