import { useCallback, useEffect, useState } from 'react'
import { describeError } from '../api/client'

export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: T }

type Settled<T> = { load: unknown; tick: number; state: AsyncState<T> }

/**
 * Gọi một hàm bất đồng bộ và theo dõi trạng thái: đang tải, lỗi, hoặc có dữ liệu.
 *
 * `load` PHẢI ổn định giữa các lần render (bọc bằng useCallback, hoặc là hàm ở ngoài
 * component): hook chạy lại mỗi khi `load` đổi. Đây cũng là cách để "đổi tham số thì tải
 * lại", ví dụ useCallback phụ thuộc vào mặt hàng đang chọn.
 *
 * Kết quả được gắn với `load` và lần tải (tick) đã sinh ra nó. Khi `load` đổi, kết quả cũ
 * không còn khớp nên hook tự trả "loading" mà không cần setState đồng bộ trong effect.
 */
export function useAsync<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
      .then((data) => setSettled({ load, tick, state: { status: 'ready', data } }))
      .catch((error: unknown) => {
        // Request bị huỷ có chủ ý (đổi lựa chọn, gỡ component) thì không phải lỗi.
        if (controller.signal.aborted) return
        setSettled({ load, tick, state: { status: 'error', message: describeError(error) } })
      })
    return () => controller.abort()
  }, [load, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])

  const state: AsyncState<T> =
    settled && settled.load === load && settled.tick === tick
      ? settled.state
      : { status: 'loading' }

  return { state, reload }
}
