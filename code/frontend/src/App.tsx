import { Routes, Route } from 'react-router-dom';

// Pages
import Homepage from './pages/Homepage';
import Loginpage from './pages/Loginpage';
import Mypage from './pages/Mypage';

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
          } />
      </Routes>
    </div>
  );
}