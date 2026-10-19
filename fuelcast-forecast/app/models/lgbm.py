"""Lớp "học máy": LightGBM đoán PHẦN DƯ mà mô hình truyền dẫn để lại.

    phần dư = Δ bán lẻ thực tế - Δ bán lẻ mô hình truyền dẫn đoán

Dữ liệu rất ít (cỡ vài chục đến một trăm kỳ cho mỗi mặt hàng), nên mọi tham số đều chọn theo
hướng KHÔNG cho phép quá khớp: cây nhỏ, mỗi lá phải có nhiều mẫu, ít vòng, học chậm. Mô hình
này phải tự chứng minh bằng backtest rằng nó hơn lớp luật; nếu không thì không có lý do để
dùng nó, và bảng điểm sẽ nói thẳng điều đó.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import lightgbm as lgb
import numpy as np

# Dưới ngưỡng này không huấn luyện: cây trên ít hơn 30 mẫu chỉ là học thuộc lòng nhiễu.
MIN_ROWS = 30

PARAMS = {
    "objective": "regression",
    "learning_rate": 0.05,
    "num_leaves": 4,
    "max_depth": 2,
    "min_data_in_leaf": 10,
    "min_data_in_bin": 3,
    "feature_fraction": 0.8,
    "lambda_l2": 10.0,
    # Mặc định LightGBM tiền lọc cột theo min_data_in_leaf lúc dựng Dataset; với mẫu nhỏ
    # nó có thể loại cột đi mà không báo.
    "feature_pre_filter": False,
    # Cùng dữ liệu phải cho cùng kết quả: backtest và dự báo không được lệch nhau theo lần chạy.
    "deterministic": True,
    "force_row_wise": True,
    "num_threads": 1,
    "seed": 42,
    "verbosity": -1,
}
ROUNDS = 60


@dataclass(frozen=True)
class ResidualModel:
    booster: lgb.Booster

    @classmethod
    def fit(
        cls, features: Sequence[Sequence[float]], residuals: Sequence[float]
    ) -> ResidualModel | None:
        """None khi chưa đủ dữ liệu hoặc phần dư gần như bằng 0 (không còn gì để học)."""
        x = np.asarray(features, dtype=float)
        y = np.asarray(residuals, dtype=float)
        if len(y) < MIN_ROWS or float(np.std(y)) < 1e-9:
            return None
        booster = lgb.train(PARAMS, lgb.Dataset(x, label=y), num_boost_round=ROUNDS)
        return cls(booster)

    def predict(self, features: Sequence[float]) -> float:
        return float(self.booster.predict(np.asarray([features], dtype=float))[0])
