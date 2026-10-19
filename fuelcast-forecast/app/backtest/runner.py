"""Backtest walk-forward: đi theo thời gian, mỗi kỳ chỉ học từ các kỳ trước nó.

Ba bước và thứ tự của chúng là toàn bộ chuyện chống leakage: (1) lấy các dòng huấn luyện CHỈ
từ những kỳ đã xảy ra, (2) học, (3) đoán kỳ này. Đảo thứ tự, hoặc để đặc trưng của kỳ i nhìn
thấy dữ liệu sau kỳ i, là lỗi làm con số đẹp một cách giả tạo.

Mỗi lần đoán so với hai đường cơ sở: "không đổi" (Δ = 0) và lớp luật đứng một mình. Mô hình
đầy đủ chỉ đáng giữ nếu thắng CẢ HAI.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Sequence

from app.features import Mode, Period, build_rows
from app.predictor import MIN_TRAIN_ROWS, fit, predict


@dataclass(frozen=True)
class Step:
    index: int
    at: datetime
    actual: float
    rule: float
    combined: float
    train_rows: int
    used_residual: bool


def walk_forward(
    periods: Sequence[Period], mode: Mode, min_train: int = MIN_TRAIN_ROWS
) -> list[Step]:
    rows = build_rows(periods, mode)
    steps: list[Step] = []
    for k, row in enumerate(rows):
        train = rows[:k]  # chỉ các kỳ đã xảy ra
        if len(train) < min_train:
            continue
        fitted = fit(train)
        if fitted is None:
            continue
        assert row.target is not None
        p = predict(fitted, row.features)
        steps.append(
            Step(
                index=row.index,
                at=row.at,
                actual=row.target,
                rule=p.rule_delta,
                combined=p.delta,
                train_rows=len(train),
                used_residual=p.used_residual,
            )
        )
    return steps


@dataclass(frozen=True)
class Summary:
    n: int
    mae_naive: float
    mae_rule: float
    mae_combined: float
    hit_rule: float  # tỷ lệ đoán đúng chiều, trong [0, 1]
    hit_combined: float

    @property
    def gain_rule(self) -> float:
        return _gain(self.mae_naive, self.mae_rule)

    @property
    def gain_combined(self) -> float:
        return _gain(self.mae_naive, self.mae_combined)


def _gain(baseline: float, model: float) -> float:
    """Phần sai số giảm được so với đường cơ sở; âm nghĩa là tệ hơn."""
    return 0.0 if baseline == 0 else 1 - model / baseline


def _sign(x: float) -> int:
    return (x > 0) - (x < 0)


def _same_direction(predicted: float, actual: float) -> bool:
    """Đúng chiều nghĩa là cùng dấu (tăng/giảm/giữ nguyên), như signum bên Java."""
    return _sign(predicted) == _sign(actual)


def summarise(steps: Sequence[Step]) -> Summary:
    n = len(steps)
    if n == 0:
        return Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0)
    return Summary(
        n=n,
        mae_naive=sum(abs(s.actual) for s in steps) / n,
        mae_rule=sum(abs(s.actual - s.rule) for s in steps) / n,
        mae_combined=sum(abs(s.actual - s.combined) for s in steps) / n,
        hit_rule=sum(_same_direction(s.rule, s.actual) for s in steps) / n,
        hit_combined=sum(_same_direction(s.combined, s.actual) for s in steps) / n,
    )
