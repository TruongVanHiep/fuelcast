import { useMemo } from 'react'
import type { ScorecardRow } from '../../api/types'
import { useChartTheme, useECharts } from '../../hooks/useECharts'
import { formatNumber } from '../../lib/format'

/**
 * Sai số trung bình (MAE, đồng/lít) của mô hình đặt cạnh đường cơ sở "giá không đổi".
 * Cột mô hình thấp hơn cột cơ sở nghĩa là mô hình có giá trị; cao hơn nghĩa là đoán
 * "không đổi" còn tốt hơn.
 */
export default function AccuracyChart({ rows }: { rows: ScorecardRow[] }) {
  const theme = useChartTheme()

  const option = useMemo(() => {
    // Nhiều phiên bản mô hình trên cùng mặt hàng thì phải nhãn rõ từng phiên bản.
    const labels = rows.map((r) => `${r.name}\n${r.modelVersion}`)
    return {
      animation: false,
      textStyle: { color: theme.text },
      legend: { top: 0, textStyle: { color: theme.text } },
      grid: { left: 64, right: 20, top: 40, bottom: 56 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: theme.surface,
        borderColor: theme.border,
        textStyle: { color: theme.text },
        valueFormatter: (v: number) => `${formatNumber(Math.round(v))} đ`,
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisLine: { lineStyle: { color: theme.border } },
        axisLabel: { color: theme.muted, interval: 0 },
      },
      yAxis: {
        // Đơn vị (đ/lít) nằm ở tiêu đề thẻ, không đặt tên trục: tên trục chồng lên chú giải
        // khi màn hình hẹp.
        type: 'value',
        axisLabel: { color: theme.muted, formatter: (v: number) => formatNumber(v) },
        splitLine: { lineStyle: { color: theme.border } },
      },
      series: [
        {
          name: 'Mô hình',
          type: 'bar',
          itemStyle: { color: theme.accent },
          data: rows.map((r) => r.maeModelVnd),
        },
        {
          name: 'Giá không đổi',
          type: 'bar',
          itemStyle: { color: theme.muted },
          data: rows.map((r) => r.maeNaiveVnd),
        },
      ],
    }
  }, [rows, theme])

  const ref = useECharts(option)

  return (
    <div
      ref={ref}
      role="img"
      aria-label="Biểu đồ so sánh sai số trung bình của mô hình và đường cơ sở giá không đổi"
      className="h-72 w-full"
    />
  )
}
