import { useEffect, useState, type ReactNode } from 'react'
import { initialLiveState, LiveContext, type LiveState } from './LiveContext'

const STREAM_URL = '/api/v1/stream/prices'

// Trình duyệt tự nối lại khi mất kết nối, trừ khi máy chủ trả lỗi hẳn (ví dụ 503 vì quá nhiều kết
// nối): khi đó nó bỏ cuộc. Tự thử lại sau chừng này thay vì để trang mất cập nhật im lặng.
const RETRY_AFTER_GIVE_UP_MS = 30_000

/**
 * Mở MỘT kết nối SSE cho cả ứng dụng và chia sẻ trạng thái của nó qua context. Một kết nối chung
 * thay vì mỗi trang một cái: máy chủ giới hạn số kết nối, và chuyển trang không nên đóng mở lại.
 *
 * SSE ở đây chỉ là tín hiệu "có gì vừa đổi"; số liệu vẫn lấy từ REST. Nên khi kết nối mất rồi nối
 * lại, ta không biết đã lỡ những sự kiện nào: tăng cả hai số phiên bản để mọi trang tải lại.
 */
export default function LiveProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<LiveState>(() =>
    typeof EventSource === 'undefined' ? { ...initialLiveState, status: 'offline' } : initialLiveState,
  )

  useEffect(() => {
    if (typeof EventSource === 'undefined') return

    let source: EventSource | null = null
    let retryTimer: number | undefined
    let wasOffline = false

    const connect = () => {
      const es = new EventSource(STREAM_URL)
      source = es

      es.onopen = () => {
        const resync = wasOffline
        wasOffline = false
        setState((s) => ({
          ...s,
          status: 'live',
          pricesVersion: s.pricesVersion + (resync ? 1 : 0),
          forecastsVersion: s.forecastsVersion + (resync ? 1 : 0),
        }))
      }

      es.addEventListener('prices', () =>
        setState((s) => ({ ...s, pricesVersion: s.pricesVersion + 1, lastPricesAt: Date.now() })),
      )
      es.addEventListener('forecasts', () =>
        setState((s) => ({ ...s, forecastsVersion: s.forecastsVersion + 1 })),
      )

      es.onerror = () => {
        wasOffline = true
        setState((s) => ({ ...s, status: 'offline' }))
        if (es.readyState === EventSource.CLOSED) {
          es.close()
          retryTimer = window.setTimeout(connect, RETRY_AFTER_GIVE_UP_MS)
        }
      }
    }

    connect()
    return () => {
      window.clearTimeout(retryTimer)
      source?.close()
    }
  }, [])

  return <LiveContext.Provider value={state}>{children}</LiveContext.Provider>
}
