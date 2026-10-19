"""API của dịch vụ dự báo. Không giữ trạng thái và không đọc DB: fuelcast-core là nguồn sự thật,
gửi chuỗi các kỳ trong mỗi request. Dữ liệu chỉ vài chục đến vài trăm dòng nên huấn luyện lại
trong mỗi request chỉ mất vài chục mili giây, và nhờ vậy không có "mô hình đã lưu" nào có thể
lỗi thời so với dữ liệu."""

from __future__ import annotations

import math
from datetime import timedelta

from fastapi import FastAPI, HTTPException

from app.backtest.runner import summarise, walk_forward
from app.features import FEATURE_NAMES, Period, build_row, build_rows
from app.predictor import MIN_TRAIN_ROWS, MODEL_VERSION, fit, predict
from app.schemas import (
    BacktestRequest,
    BacktestResponse,
    PeriodIn,
    PredictRequest,
    PredictResponse,
    StepOut,
    SummaryOut,
)

app = FastAPI(
    title="fuelcast-forecast",
    version="0.1.0",
    description="Mô hình luật + LightGBM phần dư cho dự báo mức đổi giá xăng dầu theo kỳ.",
)


def _periods(items: list[PeriodIn]) -> list[Period]:
    return [Period(p.at, p.delta_vnd, p.delta_usd) for p in items]


def _json_number(x: float) -> float | None:
    """JSON không có NaN; đặc trưng thiếu được trả là null."""
    return None if math.isnan(x) else x


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "model_version": MODEL_VERSION}


@app.post("/predict", response_model=PredictResponse)
def predict_next(req: PredictRequest) -> PredictResponse:
    """Đoán mức đổi giá của kỳ SẮP TỚI. Chỉ có chế độ "lagged": dùng giá thế giới của kỳ vừa
    rồi, thứ duy nhất có trước giờ công bố."""
    periods = _periods(req.periods)
    if not periods:
        raise HTTPException(422, "Cần ít nhất một kỳ.")

    train = build_rows(periods, "lagged")
    fitted = fit(train)
    if fitted is None:
        raise HTTPException(
            422,
            f"Không đủ dữ liệu: có {len(train)} cặp liền kề dùng được, cần ít nhất "
            f"{MIN_TRAIN_ROWS} và giá thế giới phải thay đổi giữa các kỳ.",
        )

    target_at = req.target_at or periods[-1].at + timedelta(days=7)
    row = build_row(periods, len(periods), "lagged", at=target_at)
    assert row is not None  # kỳ chưa xảy ra luôn dùng được
    p = predict(fitted, row.features)

    return PredictResponse(
        model_version=MODEL_VERSION,
        predicted_delta_vnd=p.delta,
        rule_delta_vnd=p.rule_delta,
        residual_delta_vnd=p.residual_delta,
        used_residual=p.used_residual,
        train_rows=fitted.n_rows,
        features={n: _json_number(v) for n, v in zip(FEATURE_NAMES, row.features)},
        explanation={
            "rule_effect": fitted.rule.slope * row.features[0],
            "intercept": fitted.rule.intercept,
            "residual_model": p.residual_delta,
            "net_delta": p.delta,
        },
    )


@app.post("/backtest", response_model=BacktestResponse)
def backtest(req: BacktestRequest) -> BacktestResponse:
    """Walk-forward trên chuỗi đã gửi, so với "không đổi" và lớp luật đứng một mình."""
    steps = walk_forward(_periods(req.periods), req.mode, req.min_train)
    s = summarise(steps)
    return BacktestResponse(
        model_version=MODEL_VERSION,
        mode=req.mode,
        summary=SummaryOut(
            n=s.n,
            mae_naive=s.mae_naive,
            mae_rule=s.mae_rule,
            mae_combined=s.mae_combined,
            hit_rule=s.hit_rule,
            hit_combined=s.hit_combined,
            gain_rule=s.gain_rule,
            gain_combined=s.gain_combined,
            beats_naive=s.n > 0 and s.mae_combined < s.mae_naive,
            beats_rule=s.n > 0 and s.mae_combined < s.mae_rule,
        ),
        steps=[
            StepOut(
                at=st.at,
                actual_delta_vnd=st.actual,
                rule_delta_vnd=st.rule,
                predicted_delta_vnd=st.combined,
                train_rows=st.train_rows,
                used_residual=st.used_residual,
            )
            for st in steps
        ],
    )
