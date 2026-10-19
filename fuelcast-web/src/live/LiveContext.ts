import { createContext, useContext } from 'react'

/**
 * Trạng thái của luồng cập nhật trực tiếp (Server-Sent Events).
 *
 * Các số `...Version` tăng lên mỗi khi có sự kiện tương ứng. Trang nào muốn tự làm mới dữ liệu
 * thì truyền số đó cho useAsync: đổi số là tải lại, và (khác với đổi tham số) vẫn giữ dữ liệu cũ
 * trên màn hình tới khi dữ liệu mới về, không nháy "đang tải".
 */
export type LiveStatus = 'connecting' | 'live' | 'offline'

export interface LiveState {
  status: LiveStatus
  /** Tăng khi vừa nạp bản tin mới: giá, kỳ điều hành. */
  pricesVersion: number
  /** Tăng khi dự báo LIVE vừa được tạo hoặc chấm điểm. */
  forecastsVersion: number
  /** Lúc (ms) nhận sự kiện bản tin mới gần nhất; null nếu chưa có trong phiên này. */
  lastPricesAt: number | null
}

export const initialLiveState: LiveState = {
  status: 'connecting',
  pricesVersion: 0,
  forecastsVersion: 0,
  lastPricesAt: null,
}

export const LiveContext = createContext<LiveState>(initialLiveState)

export const useLive = () => useContext(LiveContext)
