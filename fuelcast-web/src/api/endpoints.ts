import { http } from './client'
import type {
  CurrentCycle,
  CurrentPrice,
  NextForecasts,
  PriceHistory,
  Scorecard,
} from './types'

// Mỗi hàm một endpoint của backend. Nhận AbortSignal để huỷ request khi component
// bị gỡ hoặc người dùng đổi lựa chọn giữa chừng.

export const getCurrentCycle = (signal?: AbortSignal) =>
  http.get<CurrentCycle>('/cycles/current', { signal }).then((r) => r.data)

export const getCurrentPrices = (signal?: AbortSignal) =>
  http.get<CurrentPrice[]>('/prices/current', { signal }).then((r) => r.data)

export const getPriceHistory = (
  product: string,
  from?: string,
  to?: string,
  signal?: AbortSignal,
) =>
  http
    .get<PriceHistory>('/prices/history', { params: { product, from, to }, signal })
    .then((r) => r.data)

export const getNextForecasts = (signal?: AbortSignal) =>
  http.get<NextForecasts>('/forecasts/next', { signal }).then((r) => r.data)

export const getScorecard = (signal?: AbortSignal) =>
  http.get<Scorecard>('/accuracy/scorecard', { signal }).then((r) => r.data)
