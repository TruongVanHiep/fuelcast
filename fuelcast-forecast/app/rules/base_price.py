"""Lớp "luật": mô hình nền, giải thích được bằng một công thức.

Thiết kế ban đầu muốn một rule engine dựng giá cơ sở từ giá Platts, thuế, premium nhập khẩu...
Những số đó cần nguồn dữ liệu theo ngày mà dự án không có (xem docs/data-sources.md), nên lớp
luật ở đây là phần truyền dẫn duy nhất đo được từ dữ liệu có thật:

    Δ giá bán lẻ (đồng/lít) = hệ số × Δ giá thế giới (USD/thùng) + hằng số

Hệ số học bằng bình phương tối thiểu. Lớp "học máy" (models/lgbm.py) chỉ sửa phần dư mà
công thức này để lại, đúng tinh thần "ML hiệu chỉnh phần dư" của thiết kế.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import numpy as np


@dataclass(frozen=True)
class PassThroughRule:
    slope: float  # đồng/lít trên mỗi USD/thùng
    intercept: float  # đồng/lít

    @classmethod
    def fit(cls, x: Sequence[float], y: Sequence[float]) -> PassThroughRule | None:
        """Bình phương tối thiểu. None khi không học được: ít hơn 2 điểm, hoặc x không đổi
        (mẫu số bằng 0, đường thẳng không xác định)."""
        xs = np.asarray(x, dtype=float)
        ys = np.asarray(y, dtype=float)
        if xs.size < 2:
            return None
        var = float(np.var(xs))
        if var < 1e-12:
            return None
        slope = float(np.cov(xs, ys, bias=True)[0, 1] / var)
        intercept = float(ys.mean() - slope * xs.mean())
        return cls(slope=slope, intercept=intercept)

    def predict(self, x: float) -> float:
        return self.slope * x + self.intercept
