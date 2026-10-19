import { NavLink, Outlet } from 'react-router-dom'

const links = [
  { to: '/', label: 'Tổng quan', end: true },
  { to: '/forecast', label: 'Dự báo', end: false },
  { to: '/scorecard', label: 'Bảng điểm', end: false },
  { to: '/history', label: 'Lịch sử giá', end: false },
]

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-8 gap-y-2 px-4 py-3">
          <span className="text-lg font-semibold tracking-tight">
            Fuel<span className="text-accent">Cast</span>
          </span>
          <nav aria-label="Điều hướng chính" className="flex flex-wrap gap-1">
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  `rounded-md px-3 py-1.5 text-sm font-medium ${
                    isActive ? 'bg-accent/15 text-accent' : 'text-muted hover:text-text'
                  }`
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      {/* Lời miễn trừ bắt buộc ở mọi trang, theo tài liệu thiết kế (mục Disclaimer). */}
      <footer className="border-t border-border bg-surface">
        <p className="mx-auto max-w-5xl px-4 py-4 text-xs leading-relaxed text-muted">
          Dữ liệu và dự báo trên hệ thống chỉ mang tính tham khảo, được tạo tự động từ dữ liệu
          công khai của Bộ Công Thương. Đây không phải khuyến nghị đầu tư hay cơ sở giao dịch.
          Giá bán lẻ chính thức căn cứ theo công bố của cơ quan quản lý và thương nhân đầu mối.
        </p>
      </footer>
    </div>
  )
}
