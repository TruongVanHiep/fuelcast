import { useMemo } from 'react'
import type { PricePoint } from '../../api/types'
import { useChartTheme, useECharts } from '../../hooks/useECharts'
import { formatDateTime, formatNumber } from '../../lib/format'

/**
 * Lịch sử giá một mặt hàng. Vẽ dạng bậc thang chứ không nối thẳng: giá bán lẻ giữ nguyên
 * cho tới lần công bố kế tiếp, nối thẳng giữa hai điểm sẽ vẽ ra những mức giá chưa từng có.
 */
export default function PriceChart({
  points,
  name,
  unit,
}: {
  points: PricePoint[]
  name: string
  unit: string
}) {
  const theme = useChartTheme()

  const option = useMemo(
    () => ({
      animation: false,
      textStyle: { color: theme.text },
      grid: { left: 64, right: 20, top: 20, bottom: 64 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: theme.surface,
        borderColor: theme.border,
        textStyle: { color: theme.text },
        formatter: (params: { value: [number, number] }[]) => {
          const [time, price] = params[0].value
          return `${formatDateTime(new Date(time).toISOString())}<br/><b>${formatNumber(price)} đ</b> / ${unit}`
        },
      },
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: theme.border } },
        axisLabel: { color: theme.muted },
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: { color: theme.muted, formatter: (v: number) => formatNumber(v) },
        splitLine: { lineStyle: { color: theme.border } },
      },
      dataZoom: [
        { type: 'inside' },
        {
          type: 'slider',
          height: 22,
          bottom: 12,
          borderColor: theme.border,
          textStyle: { color: theme.muted },
        },
      ],
      series: [
        {
          name,
          type: 'line',
          step: 'end',
          showSymbol: points.length < 60,
          symbolSize: 5,
          lineStyle: { color: theme.accent, width: 2 },
          itemStyle: { color: theme.accent },
          data: points.map((p) => [new Date(p.effectiveAt).getTime(), p.priceVnd]),
        },
      ],
    }),
    [points, name, unit, theme],
  )

  const ref = useECharts(option)

  return (
    <div
      ref={ref}
      role="img"
      aria-label={`Biểu đồ lịch sử giá ${name}, ${points.length} lần điều chỉnh`}
      className="h-80 w-full"
    />
  )
}
