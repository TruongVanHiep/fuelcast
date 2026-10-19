import { useEffect, useState } from 'react'
import { useLive, type LiveStatus } from '../live/LiveContext'

const view: Record<LiveStatus, { label: string; dot: string; text: string }> = {
  live: { label: 'Trực tiếp', dot: 'bg-down', text: 'text-down' },
  connecting: { label: 'Đang kết nối…', dot: 'bg-muted', text: 'text-muted' },
  offline: { label: 'Mất kết nối, đang thử lại', dot: 'bg-up', text: 'text-up' },
}

/**
 * Cho người dùng biết dữ liệu có đang tự cập nhật hay không. Luôn có chữ đi kèm chấm màu (màu
 * không đứng một mình), và là vùng role="status" nên trình đọc màn hình đọc khi trạng thái đổi.
 */
export function LiveIndicator() {
  const { status } = useLive()
  const v = view[status]
  return (
    <span role="status" className={`flex items-center gap-1.5 text-xs font-medium ${v.text}`}>
      <span aria-hidden="true" className={`inline-block size-2 rounded-full ${v.dot}`} />
      {v.label}
    </span>
  )
}

const BANNER_MS = 15_000

/**
 * Thông báo ngắn khi vừa có bản tin giá mới. Dữ liệu trên trang đã tự tải lại; dòng này để người
 * đang nhìn màn hình hiểu vì sao con số vừa đổi. Tự biến mất sau 15 giây.
 */
export function NewBulletinBanner() {
  const { lastPricesAt } = useLive()
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (lastPricesAt === null) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [lastPricesAt])

  if (lastPricesAt === null || now - lastPricesAt > BANNER_MS) return null
  return (
    <p
      role="status"
      className="border-b border-warn-border bg-warn-bg px-4 py-2 text-center text-sm font-medium text-warn-text"
    >
      Vừa có bản tin điều hành giá mới. Số liệu trên trang đã được cập nhật.
    </p>
  )
}
