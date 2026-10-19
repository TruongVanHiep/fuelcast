import type { ReactNode } from 'react'
import type { AsyncState } from '../hooks/useAsync'

/** Khung chung cho mọi khối dữ liệu: một thẻ có tiêu đề. */
export function Card({
  title,
  children,
  className = '',
}: {
  title?: string
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`rounded-xl border border-border bg-surface p-4 ${className}`}>
      {title && <h2 className="mb-3 text-sm font-semibold text-muted">{title}</h2>}
      {children}
    </section>
  )
}

export function Loading() {
  return (
    <p role="status" className="py-6 text-center text-sm text-muted">
      Đang tải…
    </p>
  )
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="py-6 text-center text-sm">
      <p className="text-up">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-md border border-border px-3 py-1.5 text-text hover:bg-bg"
        >
          Thử lại
        </button>
      )}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-sm text-muted">{children}</p>
}

/**
 * Hiện đúng một trong ba trạng thái của useAsync. Gom vào một chỗ để mọi trang xử lý
 * "đang tải / lỗi / có dữ liệu" giống hệt nhau và không trang nào quên nhánh lỗi.
 */
export function Async<T>({
  state,
  onRetry,
  children,
}: {
  state: AsyncState<T>
  onRetry?: () => void
  children: (data: T) => ReactNode
}) {
  if (state.status === 'loading') return <Loading />
  if (state.status === 'error') return <ErrorBox message={state.message} onRetry={onRetry} />
  return <>{children(state.data)}</>
}
