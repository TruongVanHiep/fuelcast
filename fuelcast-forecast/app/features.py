"""Dựng bảng đặc trưng từ chuỗi các kỳ điều hành giá.

Mỗi kỳ là một lần công bố: giá bán lẻ đổi `delta_vnd` đồng khi giá thế giới trung bình của
kỳ đó đổi `delta_usd` USD/thùng so với kỳ trước. Cách ghép kỳ (ngày bắt đầu/kết thúc, kiểm tra
liên tục) do fuelcast-core làm; ở đây chỉ nhận chuỗi đã ghép.

Quy tắc sống còn của cả dự án là KHÔNG NHÌN TƯƠNG LAI. Nó được giữ ở một chỗ duy nhất, là
hàm `build_row`: đặc trưng của kỳ i chỉ được lấy từ dữ liệu có trước giờ công bố kỳ i.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Literal, Sequence

# Hai kỳ liền kề thật cách nhau 5 đến 9 ngày (chu kỳ 7 ngày, lệch khi dời lịch). Khớp với
# BacktestService.MIN_GAP/MAX_GAP bên Java để hai mô hình học trên cùng tập cặp dữ liệu.
MIN_GAP = timedelta(days=5)
MAX_GAP = timedelta(days=9)

# "lagged": đoán Δ bán lẻ của kỳ i bằng Δ giá thế giới của kỳ i-1. Đây là thứ duy nhất có
#   trước giờ công bố, nên là chế độ DỰ BÁO THẬT (LIVE).
# "same_period": dùng Δ giá thế giới của chính kỳ i, thứ chỉ đầy đủ khi bản tin đã ra.
#   Đó là GIẢI THÍCH một kỳ đã biết (REPLAY, horizon 0), không phải dự báo.
Mode = Literal["lagged", "same_period"]

FEATURE_NAMES = ("usd_in", "usd_in_prev", "vnd_prev", "vnd_prev2", "month")


@dataclass(frozen=True)
class Period:
    at: datetime
    delta_vnd: float
    delta_usd: float


@dataclass(frozen=True)
class Row:
    """Một dòng huấn luyện/đoán: đặc trưng, và đáp án nếu đã biết."""

    index: int
    at: datetime
    features: tuple[float, ...]  # thứ tự theo FEATURE_NAMES; NaN nếu thiếu
    target: float | None


def consecutive(before: Period, after: Period) -> bool:
    return MIN_GAP <= after.at - before.at <= MAX_GAP


def build_row(
    periods: Sequence[Period],
    i: int,
    mode: Mode,
    at: datetime | None = None,
) -> Row | None:
    """Đặc trưng để đoán kỳ thứ `i`. `i == len(periods)` nghĩa là kỳ CHƯA xảy ra (dự báo thật).

    Trả None khi kỳ này không dùng được (không liền kề kỳ trước, trong chế độ "lagged").
    """
    n = len(periods)
    future = i == n

    if future and mode == "same_period":
        raise ValueError("Không thể dùng giá thế giới của chính kỳ chưa xảy ra.")
    if future and at is None:
        raise ValueError("Kỳ chưa xảy ra chưa có thời điểm công bố; phải truyền `at`.")

    def link_ok(j: int) -> bool:
        """Kỳ j có liền kề kỳ j-1 không. Kỳ chưa xảy ra mặc định là có."""
        if j < 1:
            return False
        return True if j == n else consecutive(periods[j - 1], periods[j])

    if mode == "lagged" and not link_ok(i):
        return None

    src = i - 1 if mode == "lagged" else i
    usd_in = periods[src].delta_usd
    usd_in_prev = periods[src - 1].delta_usd if link_ok(src) else math.nan

    vnd_prev = periods[i - 1].delta_vnd if link_ok(i) else math.nan
    vnd_prev2 = periods[i - 2].delta_vnd if link_ok(i) and link_ok(i - 1) else math.nan

    when = at if at is not None else periods[i].at
    features = (usd_in, usd_in_prev, vnd_prev, vnd_prev2, float(when.month))
    target = None if future else periods[i].delta_vnd
    return Row(index=i, at=when, features=features, target=target)


def build_rows(periods: Sequence[Period], mode: Mode) -> list[Row]:
    """Mọi dòng có đáp án dùng được, theo thứ tự thời gian."""
    rows = (build_row(periods, i, mode) for i in range(len(periods)))
    return [row for row in rows if row is not None]
