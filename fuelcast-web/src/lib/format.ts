// Mọi thời điểm hiển thị theo giờ Việt Nam, bất kể máy người xem ở múi giờ nào:
// kỳ điều hành tính theo giờ Việt Nam (xem README: kỳ nửa đêm 27/3 hiện thành 26/3
// nếu để UTC).
const TZ = 'Asia/Ho_Chi_Minh'

const numberFormat = new Intl.NumberFormat('vi-VN')

const dateTimeFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone: TZ,
  weekday: 'short',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone: TZ,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

export const formatNumber = (n: number) => numberFormat.format(n)

/** 21.560 đ */
export const formatVnd = (n: number) => `${numberFormat.format(Math.round(n))} đ`

/** +363, −732, 0. Dùng dấu trừ thật (U+2212) cho dễ đọc, không dùng gạch nối. */
export function formatDelta(n: number): string {
  const rounded = Math.round(n)
  if (rounded > 0) return `+${numberFormat.format(rounded)}`
  if (rounded < 0) return `−${numberFormat.format(Math.abs(rounded))}`
  return '0'
}

export const formatDateTime = (iso: string) => dateTimeFormat.format(new Date(iso))

export const formatDate = (iso: string) => dateFormat.format(new Date(iso))

/** 86400 giây -> "1 ngày 00:00:00"; dưới một ngày thì chỉ "HH:MM:SS". */
export function formatCountdown(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const days = Math.floor(s / 86_400)
  const hh = String(Math.floor((s % 86_400) / 3_600)).padStart(2, '0')
  const mm = String(Math.floor((s % 3_600) / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  const clock = `${hh}:${mm}:${ss}`
  return days > 0 ? `${days} ngày ${clock}` : clock
}
