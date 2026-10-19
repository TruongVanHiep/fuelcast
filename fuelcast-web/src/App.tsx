import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import { Loading } from './components/StateBlocks'
import Dashboard from './pages/Dashboard'
import Forecast from './pages/Forecast'

// Hai trang có biểu đồ nạp theo yêu cầu: ECharts chiếm phần lớn dung lượng, mà trang tổng
// quan (trang đầu tiên người ta mở) không dùng biểu đồ nào.
const History = lazy(() => import('./pages/History'))
const Scorecard = lazy(() => import('./pages/Scorecard'))

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="forecast" element={<Forecast />} />
        <Route
          path="scorecard"
          element={
            <Suspense fallback={<Loading />}>
              <Scorecard />
            </Suspense>
          }
        />
        <Route
          path="history"
          element={
            <Suspense fallback={<Loading />}>
              <History />
            </Suspense>
          }
        />
        {/* Đường dẫn lạ thì về trang chủ thay vì màn hình trống. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
