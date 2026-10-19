import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Khi phát triển, trình duyệt gọi /api trên cổng 5173 và Vite chuyển tiếp sang Spring
    // (8080). Nhờ vậy code gọi đường dẫn tương đối "/api/v1/..." y như khi triển khai
    // (nginx cũng chuyển /api sang ứng dụng), không cần bật CORS ở backend.
    // API_TARGET để trỏ sang một backend khác (ví dụ máy chủ thử); mặc định là Spring ở 8080.
    proxy: {
      '/api': process.env.API_TARGET ?? 'http://localhost:8080',
    },
  },
})
