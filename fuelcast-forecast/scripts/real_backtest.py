"""Chạy walk-forward của dịch vụ trên DỮ LIỆU THẬT, in bảng so với "không đổi".

Dữ liệu vào là một file CSV xuất từ DB (xem README, mục "Đo trên dữ liệu thật"). Các kỳ được
ghép đúng như `BacktestService.alignPeriods` bên Java: chỉ lấy hai kỳ liền kề thật (cách nhau
5 đến 9 ngày) và có mức đổi giá khớp chênh lệch giá, để hai bên học trên cùng tập cặp dữ liệu.

    python scripts/real_backtest.py real.csv
"""

from __future__ import annotations

import csv
import sys
from datetime import datetime, timedelta
from itertools import groupby
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.backtest.runner import summarise, walk_forward  # noqa: E402
from app.features import MAX_GAP, MIN_GAP, Period  # noqa: E402


def parse_time(text: str) -> datetime:
    """'2024-01-04 15:00:00+07' (kiểu psql) thành datetime có múi giờ."""
    return datetime.fromisoformat(text.replace("+07", "+07:00"))


def align(rows: list[dict[str, str]]) -> list[Period]:
    periods: list[Period] = []
    prev_at = prev_usd = prev_price = None
    for r in rows:
        at = parse_time(r["observed_at"])
        usd = float(r["price_usd"])
        price = float(r["price_vnd"])
        delta = float(r["delta_vnd"]) if r["delta_vnd"] else None
        if prev_at is not None and delta is not None:
            continuous = abs(delta - (price - prev_price)) < 1e-6
            if continuous and MIN_GAP <= at - prev_at <= MAX_GAP:
                periods.append(Period(at, delta, usd - prev_usd))
        prev_at, prev_usd, prev_price = at, usd, price
    return periods


def main(path: str) -> None:
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    header = f"{'mặt hàng':10} {'chế độ':12} {'n':>4} {'không đổi':>10} {'luật':>6} {'luật+ML':>8} {'đúng chiều (luật/ML)':>22} {'giảm sai số (luật/ML)':>23}"
    print(header)
    for code, group in groupby(rows, key=lambda r: r["code"]):
        periods = align(list(group))
        for mode in ("lagged", "same_period"):
            s = summarise(walk_forward(periods, mode))
            print(
                f"{code:10} {mode:12} {s.n:4d} {s.mae_naive:10.0f} {s.mae_rule:6.0f} "
                f"{s.mae_combined:8.0f} {s.hit_rule * 100:9.0f}% /{s.hit_combined * 100:4.0f}% "
                f"{s.gain_rule * 100:11.1f}% /{s.gain_combined * 100:5.1f}%"
            )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
