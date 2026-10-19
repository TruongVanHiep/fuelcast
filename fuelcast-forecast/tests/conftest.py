"""Dữ liệu tổng hợp cho test. Tự dựng để biết CHẮC chân lý nằm ở đâu, nhờ đó test được cả
"tìm ra quy luật có thật" lẫn "không bịa ra quy luật không có"."""

from __future__ import annotations

import random
from datetime import datetime, timedelta, timezone

from app.features import Period

ICT = timezone(timedelta(hours=7))
START = datetime(2024, 1, 4, 15, 0, tzinfo=ICT)  # một chiều thứ Năm


def make_periods(
    n: int,
    *,
    truth: str = "lagged",
    slope: float = 40.0,
    noise: float = 25.0,
    seed: int = 7,
    gap_days: int = 7,
) -> list[Period]:
    """Dựng n kỳ liền kề.

    truth="lagged":      Δ bán lẻ kỳ i phụ thuộc Δ thế giới kỳ i-1 (đoán trước được).
    truth="same_period": Δ bán lẻ kỳ i phụ thuộc Δ thế giới của chính kỳ i (chỉ giải thích được).
    truth="none":        Δ bán lẻ là nhiễu thuần, không liên quan gì tới giá thế giới.
    """
    rng = random.Random(seed)
    usd = [rng.gauss(0, 3) for _ in range(n)]
    periods = []
    for i in range(n):
        if truth == "lagged":
            signal = slope * (usd[i - 1] if i > 0 else 0.0)
        elif truth == "same_period":
            signal = slope * usd[i]
        else:
            signal = 0.0
        vnd = signal + rng.gauss(0, noise)
        periods.append(Period(START + timedelta(days=gap_days * i), vnd, usd[i]))
    return periods


def to_json(periods: list[Period]) -> list[dict]:
    return [
        {"at": p.at.isoformat(), "delta_vnd": p.delta_vnd, "delta_usd": p.delta_usd}
        for p in periods
    ]
