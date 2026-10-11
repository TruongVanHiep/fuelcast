-- Phân biệt dự báo ĐÃ ĐƯA RA trước sự kiện với dự báo dựng lại từ lịch sử.
--
-- LIVE   : dự báo thật, tạo trước khi giá được công bố. Chỉ loại này mới chứng
--          minh được mô hình có khả năng dự báo.
-- REPLAY : "phát lại" walk-forward trên dữ liệu đã có. Mô hình chỉ học từ các kỳ
--          trước kỳ đang đoán, nhưng người viết đã biết đáp án và đã chọn mô hình
--          sau khi nhìn dữ liệu, nên đây là ước lượng lạc quan hơn LIVE.
--
-- Bảng điểm bắt buộc tách hai loại này; trộn lại là tự lừa mình.
ALTER TABLE forecast
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'LIVE'
        CHECK (kind IN ('LIVE', 'REPLAY'));

-- Một kỳ + một mặt hàng + một mô hình + một horizon chỉ có một dòng REPLAY,
-- nên chạy lại phát lại không nhân đôi dữ liệu (INSERT ... ON CONFLICT DO NOTHING).
-- Chỉ áp cho REPLAY: dự báo LIVE được phép có nhiều bản trong cùng một kỳ vì mô
-- hình cập nhật mỗi ngày (horizon_days giảm dần), đó chính là điều cần theo dõi.
CREATE UNIQUE INDEX uq_forecast_replay
    ON forecast (cycle_id, product_id, model_version, horizon_days)
    WHERE kind = 'REPLAY';
