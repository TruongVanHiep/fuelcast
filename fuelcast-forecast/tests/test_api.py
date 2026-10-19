import math

import pytest
from fastapi.testclient import TestClient

from app.api import app
from app.predictor import MODEL_VERSION
from tests.conftest import make_periods, to_json

client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "model_version": MODEL_VERSION}


def test_predict_returns_a_decomposed_forecast():
    r = client.post("/predict", json={"periods": to_json(make_periods(80))})
    assert r.status_code == 200
    body = r.json()
    assert body["model_version"] == MODEL_VERSION
    assert body["mode"] == "lagged"
    assert math.isfinite(body["predicted_delta_vnd"])
    assert body["predicted_delta_vnd"] == pytest.approx(
        body["rule_delta_vnd"] + body["residual_delta_vnd"]
    )
    assert body["train_rows"] == 79
    assert set(body["features"]) == {"usd_in", "usd_in_prev", "vnd_prev", "vnd_prev2", "month"}
    # Phần giải thích cộng lại đúng bằng mức đổi dự báo, để vẽ biểu đồ thác nước.
    e = body["explanation"]
    assert e["rule_effect"] + e["intercept"] + e["residual_model"] == pytest.approx(e["net_delta"])


def test_predict_rejects_too_little_data():
    r = client.post("/predict", json={"periods": to_json(make_periods(10))})
    assert r.status_code == 422
    assert "Không đủ dữ liệu" in r.json()["detail"]


def test_predict_rejects_an_empty_series():
    assert client.post("/predict", json={"periods": []}).status_code == 422


def test_predict_rejects_periods_out_of_order():
    periods = to_json(make_periods(40))
    periods[10], periods[11] = periods[11], periods[10]
    r = client.post("/predict", json={"periods": periods})
    assert r.status_code == 422


def test_predict_rejects_timestamps_without_a_timezone():
    # Thiếu múi giờ thì "thứ Năm 15:00" không có nghĩa: từ chối, không đoán.
    periods = to_json(make_periods(40))
    periods[0]["at"] = "2024-01-04T15:00:00"
    assert client.post("/predict", json={"periods": periods}).status_code == 422


def test_backtest_summarises_against_both_baselines():
    body_in = {"periods": to_json(make_periods(120, truth="lagged")), "mode": "lagged"}
    r = client.post("/backtest", json=body_in)
    assert r.status_code == 200
    body = r.json()
    s = body["summary"]
    assert s["n"] == len(body["steps"]) > 0
    assert s["beats_naive"] is True
    assert s["mae_combined"] < s["mae_naive"]
    first = body["steps"][0]
    assert first["naive_delta_vnd"] == 0.0
    assert first["train_rows"] == 20


def test_backtest_defaults_to_the_honest_mode():
    r = client.post("/backtest", json={"periods": to_json(make_periods(60))})
    assert r.json()["mode"] == "lagged"


def test_backtest_same_period_mode_explains_a_known_period():
    body_in = {"periods": to_json(make_periods(120, truth="same_period")), "mode": "same_period"}
    s = client.post("/backtest", json=body_in).json()["summary"]
    assert s["gain_rule"] > 0.5
