/**
 * Lời cảnh báo do backend gửi kèm mọi phản hồi dự báo và bảng điểm. Hiện nguyên văn,
 * không rút gọn: nó là phần của dữ liệu (REPLAY khác LIVE thế nào, dự báo hiện chưa
 * hơn đoán "không đổi"), bỏ nó đi là làm người xem hiểu sai con số.
 */
export default function Notice({ text }: { text: string }) {
  if (!text) return null
  return (
    <p
      role="note"
      className="rounded-lg border border-warn-border bg-warn-bg px-3 py-2 text-sm leading-relaxed text-warn-text"
    >
      <span aria-hidden="true">⚠ </span>
      {text}
    </p>
  )
}
