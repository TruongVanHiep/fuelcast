// Kiểu dữ liệu khớp từng record DTO trong fuelcast-core (package ...core.api).
// Thời điểm là chuỗi ISO-8601 có múi giờ +07:00; ngày là "yyyy-MM-dd".
// Backend cấu hình Jackson bỏ trường null, nên trường "có thể thiếu" khai báo bằng "?".

export interface CurrentCycle {
  lastEffectiveAt: string
  lastCycleStart: string
  lastCycleEnd: string
  expectedNextAt: string
  secondsUntilNext: number
  overdue: boolean
}

export interface CurrentPrice {
  product: string
  name: string
  unit: string
  region: number
  priceVnd: number
  deltaVnd?: number | null
  effectiveAt: string
}

export interface PricePoint {
  effectiveAt: string
  priceVnd: number
  deltaVnd?: number | null
}

export interface PriceHistory {
  product: string
  name: string
  unit: string
  region: number
  from: string
  to: string
  points: PricePoint[]
}

export type Direction = 'UP' | 'DOWN' | 'FLAT'

export interface NextForecastItem {
  product: string
  name: string
  unit: string
  predictedVnd: number
  predictedDeltaVnd: number
  direction: Direction
  horizonDays: number
  modelVersion: string
  createdAt: string
  // true cho mô hình chính; các mô hình chạy song song để so sánh có primary = false.
  primary: boolean
}

export interface NextForecasts {
  notice: string
  items: NextForecastItem[]
}

export type ForecastKind = 'LIVE' | 'REPLAY'

export interface ScorecardRow {
  product: string
  name: string
  kind: ForecastKind
  modelVersion: string
  horizonDays: number
  evaluations: number
  maeModelVnd: number
  maeNaiveVnd: number
  improvementPct: number
  directionHitPct: number
  beatsNaive: boolean
  from: string
  to: string
}

export interface Scorecard {
  notice: string
  rows: ScorecardRow[]
}
