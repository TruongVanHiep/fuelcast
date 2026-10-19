import { useCallback, useState } from 'react'
import { getCurrentPrices, getPriceHistory } from '../api/endpoints'
import type { PriceHistory } from '../api/types'
import PriceChart from '../components/charts/PriceChart'
import { Async, Card, Empty } from '../components/StateBlocks'
import { useAsync } from '../hooks/useAsync'
import { formatDate } from '../lib/format'

export default function History() {
  const products = useAsync(getCurrentPrices)
  const [chosen, setChosen] = useState<string | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const list = products.state.status === 'ready' ? products.state.data : []
  // Chưa chọn thì lấy mặt hàng đầu tiên mà backend trả về.
  const product = chosen ?? list[0]?.product ?? null

  const loadHistory = useCallback(
    (signal: AbortSignal) =>
      product
        ? getPriceHistory(product, from || undefined, to || undefined, signal)
        : Promise.resolve(null),
    [product, from, to],
  )
  const history = useAsync(loadHistory)

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Lịch sử giá</h1>

      <Card>
        <Async state={products.state} onRetry={products.reload}>
          {(items) => (
            <form className="flex flex-wrap items-end gap-4" onSubmit={(e) => e.preventDefault()}>
              <div>
                <label htmlFor="product" className="mb-1 block text-xs text-muted">Mặt hàng</label>
                <select
                  id="product"
                  value={product ?? ''}
                  onChange={(e) => setChosen(e.target.value)}
                  className="rounded-md border border-border bg-bg px-2 py-1.5 text-sm"
                >
                  {items.map((p) => (
                    <option key={p.product} value={p.product}>{p.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="from" className="mb-1 block text-xs text-muted">Từ ngày</label>
                <input
                  id="from"
                  type="date"
                  value={from}
                  max={to || undefined}
                  onChange={(e) => setFrom(e.target.value)}
                  className="rounded-md border border-border bg-bg px-2 py-1.5 text-sm"
                />
              </div>
              <div>
                <label htmlFor="to" className="mb-1 block text-xs text-muted">Đến ngày</label>
                <input
                  id="to"
                  type="date"
                  value={to}
                  min={from || undefined}
                  onChange={(e) => setTo(e.target.value)}
                  className="rounded-md border border-border bg-bg px-2 py-1.5 text-sm"
                />
              </div>
              {(from || to) && (
                <button
                  type="button"
                  onClick={() => {
                    setFrom('')
                    setTo('')
                  }}
                  className="rounded-md border border-border px-3 py-1.5 text-sm text-muted hover:text-text"
                >
                  Xoá khoảng ngày
                </button>
              )}
            </form>
          )}
        </Async>
      </Card>

      <Card>
        <Async state={history.state} onRetry={history.reload}>
          {(h) =>
            h === null || h.points.length === 0 ? (
              <Empty>Không có dữ liệu trong khoảng này.</Empty>
            ) : (
              <div className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted">
                  <span>
                    {h.name}, {formatDate(h.from)} – {formatDate(h.to)}: {h.points.length} lần điều chỉnh
                  </span>
                  <button
                    type="button"
                    onClick={() => downloadCsv(h)}
                    className="rounded-md border border-border px-3 py-1.5 text-text hover:bg-bg"
                  >
                    Tải CSV
                  </button>
                </div>
                <PriceChart points={h.points} name={h.name} unit={h.unit} />
              </div>
            )
          }
        </Async>
      </Card>
    </div>
  )
}

/**
 * Xuất chuỗi giá ra CSV. Thêm BOM UTF-8 ở đầu: thiếu nó Excel mở file bằng bảng mã cũ và
 * tên mặt hàng tiếng Việt thành chữ loạn.
 */
function downloadCsv(history: PriceHistory) {
  const lines = ['effective_at,price_vnd,delta_vnd']
  for (const p of history.points) {
    lines.push(`${p.effectiveAt},${p.priceVnd},${p.deltaVnd ?? ''}`)
  }
  const blob = new Blob(['﻿' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `fuelcast-${history.product}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
