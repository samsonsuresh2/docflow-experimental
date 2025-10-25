import { Route, Routes } from 'react-router-dom';
import Login from './pages/Login';
import Home from './pages/Home';
import Upload from './pages/Upload';
import Review from './pages/Review';
import Admin from './pages/Admin';
import Audit from './pages/Audit';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/upload" element={<Upload />} />
      <Route path="/review" element={<Review />} />
      <Route path="/admin" element={<Admin />} />
      <Route path="/audit" element={<Audit />} />
    </Routes>
  );
}

export default App;
