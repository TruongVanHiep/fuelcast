import { Link } from 'react-router-dom'
import { getCurrentCycle, getCurrentPrices, getNextForecasts } from '../api/endpoints'
import type { CurrentPrice } from '../api/types'
import { DeltaBadge, DirectionBadge, Pill } from '../components/Badges'
import Countdown from '../components/Countdown'
import Notice from '../components/Notice'
import { Async, Card, Empty } from '../components/StateBlocks'
import { useAsync } from '../hooks/useAsync'
import { useLive } from '../live/LiveContext'
import { formatDate, formatDateTime, formatDelta, formatNumber } from '../lib/format'

export default function Dashboard() {
  // Giá và kỳ điều hành đổi khi có bản tin mới; dự báo đổi khi được tạo hoặc chấm điểm.
  const { pricesVersion, forecastsVersion } = useLive()
  const cycle = useAsync(getCurrentCycle, pricesVersion)
  const prices = useAsync(getCurrentPrices, pricesVersion)
  const forecasts = useAsync(getNextForecasts, forecastsVersion)

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-2">
        <Card title="Kỳ công bố giá tiếp theo (dự kiến)">
          <Async state={cycle.state} onRetry={cycle.reload}>
            {(c) => (
              <div className="space-y-2">
                {/* key làm đếm ngược tính lại đích mỗi khi dữ liệu mới về */}
                <Countdown
                  key={`${c.expectedNextAt}-${c.secondsUntilNext}`}
                  secondsUntilNext={c.secondsUntilNext}
                  overdue={c.overdue}
                />
                <p className="text-sm text-muted">
                  Dự kiến: {formatDateTime(c.expectedNextAt)}. Đây là thói quen thứ Năm 15:00,
                  không phải cam kết; khoảng 6% số kỳ lệch ngày.
                </p>
                <p className="text-sm text-muted">
                  Kỳ gần nhất: {formatDate(c.lastCycleStart)} – {formatDate(c.lastCycleEnd)}, có hiệu lực{' '}
                  {formatDateTime(c.lastEffectiveAt)}.
                </p>
              </div>
            )}
          </Async>
        </Card>

        <Card title="Dự báo cho kỳ tới">
          <Async state={forecasts.state} onRetry={forecasts.reload}>
            {(f) => {
              // Tổng quan chỉ nêu mô hình chính; các mô hình chạy song song ở trang Dự báo.
              const main = f.items.filter((item) => item.primary)
              return main.length === 0 ? (
                <Empty>Chưa có dự báo cho kỳ tới.</Empty>
              ) : (
                <div className="space-y-3">
                  <ul className="divide-y divide-border">
                    {main.map((item) => (
                      <li key={item.product} className="flex items-center justify-between gap-3 py-2 text-sm">
                        <span>{item.name}</span>
                        <span className="flex items-center gap-3">
                          <span className="tabular text-muted">{formatDelta(item.predictedDeltaVnd)} đ</span>
                          <DirectionBadge direction={item.direction} />
                        </span>
                      </li>
                    ))}
                  </ul>
                  <Notice text={f.notice} />
                  <Link to="/forecast" className="inline-block text-sm text-accent hover:underline">
                    Xem chi tiết dự báo
                  </Link>
                </div>
              )
            }}
          </Async>
        </Card>
      </div>

      <Card title="Giá bán lẻ hiện hành (vùng 1)">
        <Async state={prices.state} onRetry={prices.reload}>
          {(list) => (list.length === 0 ? <Empty>Chưa có dữ liệu giá.</Empty> : <PriceTable prices={list} />)}
        </Async>
      </Card>
    </div>
  )
}

function PriceTable({ prices }: { prices: CurrentPrice[] }) {
  // Mặt hàng đã ngừng công bố vẫn nằm trong danh sách với ngày cũ (xem CurrentPriceDto).
  // Coi là "cũ" nếu ngày hiệu lực thua ngày mới nhất của cả bảng.
  const latest = Math.max(...prices.map((p) => new Date(p.effectiveAt).getTime()))

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-muted">
            <th scope="col" className="py-2 pr-4 font-medium">Mặt hàng</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">Giá</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">So với kỳ trước</th>
            <th scope="col" className="py-2 font-medium">Hiệu lực từ</th>
          </tr>
        </thead>
        <tbody>
          {prices.map((p) => {
            const stale = new Date(p.effectiveAt).getTime() < latest
            return (
              <tr key={p.product} className="border-b border-border last:border-0">
                <th scope="row" className="py-2 pr-4 text-left font-medium">{p.name}</th>
                <td className="tabular py-2 pr-4 text-right">
                  {formatNumber(p.priceVnd)} đ<span className="text-muted">/{p.unit}</span>
                </td>
                <td className="py-2 pr-4 text-right"><DeltaBadge value={p.deltaVnd} /></td>
                <td className="py-2">
                  {formatDateTime(p.effectiveAt)} {stale && <Pill tone="warn">giá cũ</Pill>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
