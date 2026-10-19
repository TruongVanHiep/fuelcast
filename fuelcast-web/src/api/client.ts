import axios, { isAxiosError } from 'axios'

// Đường dẫn tương đối: khi phát triển Vite chuyển /api sang Spring, khi triển khai nginx
// làm việc đó (xem vite.config.ts và docker/nginx.conf).
export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
})

/**
 * Biến lỗi của axios thành một câu người dùng đọc được.
 *
 * Backend trả lỗi theo RFC 7807 (application/problem+json) nên ưu tiên trường "detail"
 * của nó; mất kết nối thì không có response nào, phải tự nói rõ.
 */
export function describeError(error: unknown): string {
  if (isAxiosError(error)) {
    if (error.response) {
      const detail = (error.response.data as { detail?: unknown } | undefined)?.detail
      if (typeof detail === 'string' && detail.length > 0) return detail
      return `Máy chủ trả lỗi ${error.response.status}.`
    }
    if (error.code === 'ECONNABORTED') return 'Máy chủ phản hồi quá lâu.'
    return 'Không kết nối được máy chủ.'
  }
  return error instanceof Error ? error.message : 'Có lỗi không xác định.'
}
