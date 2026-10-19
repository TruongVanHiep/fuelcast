import { getNextForecasts } from '../api/endpoints'
import { DirectionBadge, Pill } from '../components/Badges'
import Notice from '../components/Notice'
import { Async, Card, Empty } from '../components/StateBlocks'
import { useAsync } from '../hooks/useAsync'
import { useLive } from '../live/LiveContext'
import { formatDateTime, formatDelta, formatNumber } from '../lib/format'

export default function Forecast() {
  const { forecastsVersion } = useLive()
  const forecasts = useAsync(getNextForecasts, forecastsVersion)

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Dự báo cho kỳ tới</h1>
      <p className="text-sm leading-relaxed text-muted">
        Mỗi dự báo được tạo <strong className="text-text">trước</strong> giờ công bố, chỉ bằng thông
        tin có sẵn lúc đó, và lưu cùng thời điểm tạo. Khi bản tin ra, hệ thống tự chấm điểm nó;
        kết quả ở trang Bảng điểm.
      </p>

      <Async state={forecasts.state} onRetry={forecasts.reload}>
        {(f) => (
          <>
            <Notice text={f.notice} />
            <Card>
              {f.items.length === 0 ? (
                <Empty>Chưa có dự báo cho kỳ tới.</Empty>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border text-left text-muted">
                        <th scope="col" className="py-2 pr-4 font-medium">Mặt hàng</th>
                        <th scope="col" className="py-2 pr-4 text-right font-medium">Giá dự đoán</th>
                        <th scope="col" className="py-2 pr-4 text-right font-medium">Mức đổi</th>
                        <th scope="col" className="py-2 pr-4 font-medium">Chiều</th>
                        <th scope="col" className="py-2 pr-4 text-right font-medium">Còn (ngày)</th>
                        <th scope="col" className="py-2 pr-4 font-medium">Mô hình</th>
                        <th scope="col" className="py-2 font-medium">Tạo lúc</th>
                      </tr>
                    </thead>
                    <tbody>
                      {f.items.map((item) => (
                        <tr key={`${item.product}-${item.modelVersion}`} className="border-b border-border last:border-0">
                          <th scope="row" className="py-2 pr-4 text-left font-medium">{item.name}</th>
                          <td className="tabular py-2 pr-4 text-right">
                            {formatNumber(item.predictedVnd)} đ<span className="text-muted">/{item.unit}</span>
                          </td>
                          <td className="tabular py-2 pr-4 text-right">{formatDelta(item.predictedDeltaVnd)} đ</td>
                          <td className="py-2 pr-4"><DirectionBadge direction={item.direction} /></td>
                          <td className="tabular py-2 pr-4 text-right">{item.horizonDays}</td>
                          <td className="py-2 pr-4 text-muted">
                            {item.modelVersion} {item.primary && <Pill tone="neutral">chính</Pill>}
                          </td>
                          <td className="py-2 whitespace-nowrap">{formatDateTime(item.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
            <p className="text-xs text-muted">
              "Còn (ngày)" là số ngày từ lúc tạo dự báo tới giờ công bố dự kiến. Mức đổi tính so
              với kỳ gần nhất. Mỗi mặt hàng có thể có nhiều dòng: mô hình chính, và các mô hình
              chạy song song chỉ để so sánh trên bảng điểm. Hai mô hình có thể bất đồng về chiều.
            </p>
          </>
        )}
      </Async>
    </div>
  )
}
