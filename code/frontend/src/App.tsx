import { Routes, Route, Link } from 'react-router-dom';

// Pages
import Homepage from './pages/Homepage';

// Components
import Header from './components/Header';

export default function App() {
  return (
    <div>
        <Header />
        <Routes>
            <Route path="/" element={<Homepage />} />
        </Routes>
    </div>
  );
}