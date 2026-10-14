-- Mỗi dự báo LIVE là duy nhất theo (kỳ, mặt hàng, mô hình, horizon).
--
-- Khác với REPLAY ở V3 chỗ này: LIVE được phép có NHIỀU bản trong cùng một kỳ khi
-- mô hình cập nhật hằng ngày và horizon_days giảm dần, nên horizon_days nằm trong
-- khoá. Chỉ cùng horizon mới là trùng lặp.
--
-- Chỉ mục này làm cho việc tạo dự báo LIVE chạy lại được (ON CONFLICT DO NOTHING):
-- bộ quét gọi mỗi vài phút, và không được sinh một dòng mới ở mỗi lần gọi.
CREATE UNIQUE INDEX uq_forecast_live
    ON forecast (cycle_id, product_id, model_version, horizon_days)
    WHERE kind = 'LIVE';
