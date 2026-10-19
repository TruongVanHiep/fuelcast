import { useEffect, useState } from 'react'
import { formatCountdown } from '../lib/format'

/**
 * Đếm ngược tới lần công bố dự kiến.
 *
 * Đích tính từ "số giây còn lại" server trả về cộng với lúc component xuất hiện, không
 * từ đồng hồ máy người dùng: đồng hồ máy lệch vài phút là đếm sai. Component nhận `key`
 * từ cha để mỗi lần dữ liệu tải lại thì tính đích mới.
 *
 * `overdue` là server báo đã quá giờ mà chưa có bản tin (ví dụ dịp lễ dời ngày). Khi đó
 * không đếm về 0 mà nói thẳng là chưa công bố.
 */
export default function Countdown({
  secondsUntilNext,
  overdue,
}: {
  secondsUntilNext: number
  overdue: boolean
}) {
  const [target] = useState(() => Date.now() + secondsUntilNext * 1000)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  const remaining = Math.max(0, Math.round((target - now) / 1000))
  const late = overdue || remaining === 0

  if (late) {
    return (
      <p className="text-2xl font-semibold text-warn-text">
        Chưa công bố
        <span className="mt-1 block text-sm font-normal text-muted">
          Đã quá giờ dự kiến; kỳ có thể bị dời ngày.
        </span>
      </p>
    )
  }

  return (
    <p
      className="tabular text-3xl font-semibold"
      // Đọc cả khối một lần thay vì đọc từng giây cho người dùng trình đọc màn hình.
      aria-label={`Còn ${formatCountdown(remaining)} tới kỳ công bố dự kiến`}
      role="timer"
    >
      {formatCountdown(remaining)}
    </p>
  )
}
