"""Ghép lớp luật và lớp phần dư thành một mô hình: học từ các dòng có đáp án, rồi đoán."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from app.features import Row
from app.models.lgbm import ResidualModel
from app.rules.base_price import PassThroughRule

MODEL_VERSION = "rule-lgbm-residual-v1"

# Cùng ngưỡng với mô hình Java (LiveForecastService.MIN_TRAIN_PAIRS) để so sánh công bằng.
MIN_TRAIN_ROWS = 20


@dataclass(frozen=True)
class Fitted:
    rule: PassThroughRule
    residual: ResidualModel | None  # None khi chưa đủ dữ liệu: chỉ còn lớp luật
    n_rows: int


@dataclass(frozen=True)
class Prediction:
    rule_delta: float
    residual_delta: float
    delta: float  # tổng: cái được ghi là dự báo

    @property
    def used_residual(self) -> bool:
        return self.residual_delta != 0.0


def fit(rows: Sequence[Row]) -> Fitted | None:
    """Học từ các dòng ĐÃ có đáp án. None khi không đủ để học một đường thẳng."""
    labelled = [r for r in rows if r.target is not None]
    if len(labelled) < MIN_TRAIN_ROWS:
        return None

    rule = PassThroughRule.fit([r.features[0] for r in labelled], [r.target for r in labelled])
    if rule is None:
        return None

    # Phần dư tính trên chính dữ liệu học: mô hình phần dư học cái mà đường thẳng bỏ sót.
    residuals = [r.target - rule.predict(r.features[0]) for r in labelled]
    residual = ResidualModel.fit([r.features for r in labelled], residuals)
    return Fitted(rule=rule, residual=residual, n_rows=len(labelled))


def predict(fitted: Fitted, features: Sequence[float]) -> Prediction:
    rule_delta = fitted.rule.predict(features[0])
    residual_delta = fitted.residual.predict(features) if fitted.residual else 0.0
    return Prediction(rule_delta, residual_delta, rule_delta + residual_delta)
