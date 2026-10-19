import type { ReactNode } from 'react'
import type { Direction } from '../api/types'
import { formatDelta } from '../lib/format'

/**
 * Mức đổi giá so với kỳ trước. Màu luôn đi kèm mũi tên và dấu +/−, không để màu một mình
 * mang nghĩa. Bản tin không nêu mức đổi thì hiện gạch ngang chứ không đoán là 0.
 */
export function DeltaBadge({ value }: { value?: number | null }) {
  if (value === null || value === undefined) {
    return <span className="text-muted">—</span>
  }
  const rounded = Math.round(value)
  const tone = rounded > 0 ? 'text-up' : rounded < 0 ? 'text-down' : 'text-muted'
  const arrow = rounded > 0 ? '▲' : rounded < 0 ? '▼' : '■'
  return (
    <span className={`tabular whitespace-nowrap font-medium ${tone}`}>
      <span aria-hidden="true">{arrow} </span>
      {formatDelta(rounded)} đ
    </span>
  )
}

const directionView: Record<Direction, { label: string; tone: string; arrow: string }> = {
  UP: { label: 'Tăng', tone: 'text-up', arrow: '▲' },
  DOWN: { label: 'Giảm', tone: 'text-down', arrow: '▼' },
  FLAT: { label: 'Giữ nguyên', tone: 'text-muted', arrow: '■' },
}

export function DirectionBadge({ direction }: { direction: Direction }) {
  const view = directionView[direction]
  return (
    <span className={`whitespace-nowrap font-medium ${view.tone}`}>
      <span aria-hidden="true">{view.arrow} </span>
      {view.label}
    </span>
  )
}

/** Nhãn nhỏ dạng viên thuốc, dùng cho LIVE/REPLAY và "thắng/thua đường cơ sở". */
export function Pill({
  tone,
  children,
}: {
  tone: 'good' | 'bad' | 'neutral' | 'warn'
  children: ReactNode
}) {
  const styles = {
    good: 'border-down/40 bg-down/10 text-down',
    bad: 'border-up/40 bg-up/10 text-up',
    neutral: 'border-border bg-bg text-muted',
    warn: 'border-warn-border bg-warn-bg text-warn-text',
  }[tone]
  return (
    <span className={`inline-block whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium ${styles}`}>
      {children}
    </span>
  )
}
