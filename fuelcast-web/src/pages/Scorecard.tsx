import { useState } from 'react'
import { getScorecard } from '../api/endpoints'
import type { ForecastKind, ScorecardRow } from '../api/types'
import { Pill } from '../components/Badges'
import AccuracyChart from '../components/charts/AccuracyChart'
import Notice from '../components/Notice'
import { Async, Card, Empty } from '../components/StateBlocks'
import { useAsync } from '../hooks/useAsync'
import { formatDate, formatNumber } from '../lib/format'

const kindLabel: Record<ForecastKind, string> = {
  LIVE: 'LIVE: dự báo thật, trước sự kiện',
  REPLAY: 'REPLAY: phát lại lịch sử',
}

export default function Scorecard() {
  const scorecard = useAsync(getScorecard)
  const [chosen, setChosen] = useState<ForecastKind | null>(null)

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Bảng điểm mô hình</h1>
      <p className="text-sm leading-relaxed text-muted">
        Mô hình luôn được so với đường cơ sở đơn giản nhất: đoán giá <em>không đổi</em>. Nếu sai
        số không thấp hơn đường cơ sở thì mô hình không có giá trị cho mặt hàng đó.
      </p>

      <Async state={scorecard.state} onRetry={scorecard.reload}>
        {(data) => {
          const hasLive = data.rows.some((r) => r.kind === 'LIVE')
          // Mặc định xem LIVE nếu đã có: đó là con số trung thực; REPLAY chỉ để tham khảo.
          const kind: ForecastKind = chosen ?? (hasLive ? 'LIVE' : 'REPLAY')
          const rows = data.rows.filter((r) => r.kind === kind)

          return (
            <>
              <Notice text={data.notice} />

              <div role="group" aria-label="Loại dự báo" className="flex flex-wrap gap-2">
                {(['LIVE', 'REPLAY'] as const).map((k) => (
                  <button
                    key={k}
                    type="button"
                    aria-pressed={kind === k}
                    onClick={() => setChosen(k)}
                    className={`rounded-md border px-3 py-1.5 text-sm font-medium ${
                      kind === k
                        ? 'border-accent bg-accent/15 text-accent'
                        : 'border-border text-muted hover:text-text'
                    }`}
                  >
                    {kindLabel[k]}
                  </button>
                ))}
              </div>

              {rows.length === 0 ? (
                <Card>
                  <Empty>
                    {kind === 'LIVE'
                      ? 'Chưa có dự báo LIVE nào được chấm: cần đợi bản tin của kỳ mà dự báo nhắm tới.'
                      : 'Chưa có dữ liệu REPLAY.'}
                  </Empty>
                </Card>
              ) : (
                <>
                  <Card title="Sai số trung bình (MAE, đồng/lít): mô hình so với giá không đổi">
                    <AccuracyChart rows={rows} />
                  </Card>
                  <Card>
                    <ScoreTable rows={rows} />
                  </Card>
                </>
              )}
            </>
          )
        }}
      </Async>
    </div>
  )
}

function horizonLabel(days: number) {
  return days === 0 ? 'cùng kỳ' : `${days} ngày`
}

function ScoreTable({ rows }: { rows: ScorecardRow[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-muted">
            <th scope="col" className="py-2 pr-4 font-medium">Mặt hàng</th>
            <th scope="col" className="py-2 pr-4 font-medium">Mô hình</th>
            <th scope="col" className="py-2 pr-4 font-medium">Horizon</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">Số lần chấm</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">MAE mô hình</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">MAE không đổi</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">Giảm sai số</th>
            <th scope="col" className="py-2 pr-4 text-right font-medium">Đúng chiều</th>
            <th scope="col" className="py-2 pr-4 font-medium">Kết luận</th>
            <th scope="col" className="py-2 font-medium">Giai đoạn</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr
              key={`${r.product}-${r.modelVersion}-${r.horizonDays}`}
              className="border-b border-border last:border-0"
            >
              <th scope="row" className="py-2 pr-4 text-left font-medium">{r.name}</th>
              <td className="py-2 pr-4 text-muted">{r.modelVersion}</td>
              <td className="py-2 pr-4">{horizonLabel(r.horizonDays)}</td>
              <td className="tabular py-2 pr-4 text-right">{r.evaluations}</td>
              <td className="tabular py-2 pr-4 text-right">{formatNumber(Math.round(r.maeModelVnd))} đ</td>
              <td className="tabular py-2 pr-4 text-right">{formatNumber(Math.round(r.maeNaiveVnd))} đ</td>
              <td className={`tabular py-2 pr-4 text-right ${r.improvementPct >= 0 ? 'text-down' : 'text-up'}`}>
                {r.improvementPct.toFixed(1)}%
              </td>
              <td className="tabular py-2 pr-4 text-right">{r.directionHitPct.toFixed(0)}%</td>
              <td className="py-2 pr-4">
                {r.beatsNaive ? (
                  <Pill tone="good">hơn "không đổi"</Pill>
                ) : (
                  <Pill tone="bad">không hơn "không đổi"</Pill>
                )}
              </td>
              <td className="py-2 whitespace-nowrap text-muted">
                {formatDate(r.from)} – {formatDate(r.to)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-3 text-xs text-muted">
        MAE: sai số tuyệt đối trung bình, đồng/lít. "Giảm sai số" âm nghĩa là mô hình tệ hơn
        đường cơ sở. Horizon "cùng kỳ" nghĩa là dùng giá thế giới của chính kỳ đó (đã biết đủ),
        nên đó là giải thích chứ không phải dự báo.
      </p>
    </div>
  )
}
