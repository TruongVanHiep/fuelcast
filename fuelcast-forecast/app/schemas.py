"""Hợp đồng HTTP giữa fuelcast-core và dịch vụ dự báo. Tên trường dùng snake_case."""

from __future__ import annotations

from typing import Literal

from pydantic import AwareDatetime, BaseModel, Field, model_validator

MAX_PERIODS = 2000


class PeriodIn(BaseModel):
    at: AwareDatetime = Field(description="Thời điểm công bố, có múi giờ (ISO-8601)")
    delta_vnd: float = Field(description="Giá bán lẻ đổi bao nhiêu đồng/lít so với kỳ trước")
    delta_usd: float = Field(description="Giá thế giới trung bình của kỳ đổi bao nhiêu USD/thùng")


class _PeriodsRequest(BaseModel):
    periods: list[PeriodIn] = Field(
        max_length=MAX_PERIODS,
        description="Các kỳ theo thứ tự thời gian tăng dần; core đã ghép và kiểm tra",
    )

    @model_validator(mode="after")
    def _ascending(self) -> _PeriodsRequest:
        times = [p.at for p in self.periods]
        if any(b <= a for a, b in zip(times, times[1:])):
            raise ValueError("periods phải theo thứ tự thời gian tăng dần, không trùng thời điểm")
        return self


class PredictRequest(_PeriodsRequest):
    target_at: AwareDatetime | None = Field(
        default=None,
        description="Thời điểm công bố dự kiến của kỳ cần đoán; để lấy tháng làm đặc trưng. "
        "Bỏ trống thì lấy kỳ cuối cộng 7 ngày.",
    )


class PredictResponse(BaseModel):
    model_version: str
    mode: Literal["lagged"] = "lagged"
    predicted_delta_vnd: float
    rule_delta_vnd: float
    residual_delta_vnd: float
    used_residual: bool
    train_rows: int
    features: dict[str, float | None]
    explanation: dict[str, float]


class BacktestRequest(_PeriodsRequest):
    mode: Literal["lagged", "same_period"] = Field(
        default="lagged",
        description="lagged: dự báo thật (giá thế giới kỳ trước). "
        "same_period: giải thích kỳ đã biết (giá thế giới cùng kỳ).",
    )
    min_train: int = Field(default=20, ge=5, le=500)


class StepOut(BaseModel):
    at: AwareDatetime
    actual_delta_vnd: float
    naive_delta_vnd: float = 0.0
    rule_delta_vnd: float
    predicted_delta_vnd: float
    train_rows: int
    used_residual: bool


class SummaryOut(BaseModel):
    n: int
    mae_naive: float
    mae_rule: float
    mae_combined: float
    hit_rule: float
    hit_combined: float
    gain_rule: float
    gain_combined: float
    beats_naive: bool
    beats_rule: bool


class BacktestResponse(BaseModel):
    model_version: str
    mode: Literal["lagged", "same_period"]
    summary: SummaryOut
    steps: list[StepOut]
