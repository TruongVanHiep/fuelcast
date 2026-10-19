import { useEffect, useRef, useSyncExternalStore } from 'react'
import { BarChart, LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import * as echarts from 'echarts/core'
import type { EChartsCoreOption, ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

// Chỉ đăng ký những phần biểu đồ thật sự dùng, để bản build không kéo cả ECharts (nặng).
echarts.use([
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
  CanvasRenderer,
])

/**
 * Gắn một biểu đồ ECharts vào một thẻ div và giữ nó khớp với `option`.
 *
 * `option` PHẢI được bọc useMemo ở nơi gọi: mỗi lần nó đổi tham chiếu, biểu đồ được vẽ lại.
 * Hook lo phần khó quên: tạo biểu đồ một lần, co giãn theo khung, và huỷ khi component gỡ
 * (không huỷ là rò bộ nhớ mỗi lần chuyển trang).
 */
export function useECharts(option: EChartsCoreOption) {
  const ref = useRef<HTMLDivElement>(null)
  const chart = useRef<ECharts | null>(null)

  useEffect(() => {
    const element = ref.current
    if (!element) return
    const instance = echarts.init(element)
    chart.current = instance
    const observer = new ResizeObserver(() => instance.resize())
    observer.observe(element)
    return () => {
      observer.disconnect()
      instance.dispose()
      chart.current = null
    }
  }, [])

  useEffect(() => {
    // notMerge=true: thay hẳn cấu hình cũ, tránh sót đường/cột của lần vẽ trước.
    chart.current?.setOption(option, true)
  }, [option])

  return ref
}

/** Màu biểu đồ lấy từ biến CSS của trang, nên biểu đồ tự theo chế độ sáng/tối. */
export interface ChartTheme {
  text: string
  muted: string
  border: string
  accent: string
  surface: string
}

const darkQuery = '(prefers-color-scheme: dark)'

function subscribeToScheme(onChange: () => void) {
  const query = window.matchMedia(darkQuery)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

const isDark = () => window.matchMedia(darkQuery).matches

/**
 * Đọc bảng màu hiện hành. Phụ thuộc vào chế độ sáng/tối để tính lại khi người dùng đổi
 * giao diện hệ thống; component dùng kết quả trong useMemo cùng `option`.
 */
export function useChartTheme(): ChartTheme {
  const dark = useSyncExternalStore(subscribeToScheme, isDark, () => false)
  return readTheme(dark)
}

// Lưu theo chế độ để cùng một đối tượng trả về giữa các lần render (useMemo ở nơi gọi
// mới không vẽ lại biểu đồ vô cớ).
const cache = new Map<boolean, ChartTheme>()

function readTheme(dark: boolean): ChartTheme {
  const cached = cache.get(dark)
  if (cached) return cached
  const css = getComputedStyle(document.documentElement)
  const read = (name: string) => css.getPropertyValue(name).trim()
  const theme: ChartTheme = {
    text: read('--text'),
    muted: read('--muted'),
    border: read('--border'),
    accent: read('--accent'),
    surface: read('--surface'),
  }
  cache.set(dark, theme)
  return theme
}
