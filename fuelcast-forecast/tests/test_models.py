import math
from datetime import timedelta

import pytest

from app.models.lgbm import MIN_ROWS, ResidualModel
from app.predictor import MIN_TRAIN_ROWS, fit, predict
from app.features import build_row, build_rows
from app.rules.base_price import PassThroughRule
from tests.conftest import make_periods


def test_rule_recovers_a_known_slope():
    xs = [i / 10 for i in range(-50, 50)]
    ys = [37.5 * x + 12 for x in xs]
    rule = PassThroughRule.fit(xs, ys)
    assert rule.slope == pytest.approx(37.5)
    assert rule.intercept == pytest.approx(12)
    assert rule.predict(2.0) == pytest.approx(87.0)


def test_rule_refuses_to_fit_when_x_never_changes():
    # Đường thẳng không xác định khi giá thế giới không đổi: phải từ chối, không chia cho 0.
    assert PassThroughRule.fit([1.0] * 30, [float(i) for i in range(30)]) is None


def test_rule_refuses_a_single_point():
    assert PassThroughRule.fit([1.0], [2.0]) is None


def test_residual_model_needs_enough_rows():
    rows = [[float(i), 0.0, 0.0, 0.0, 1.0] for i in range(MIN_ROWS - 1)]
    assert ResidualModel.fit(rows, [float(i % 3) for i in range(MIN_ROWS - 1)]) is None


def test_residual_model_skips_when_there_is_nothing_to_learn():
    rows = [[float(i), 0.0, 0.0, 0.0, 1.0] for i in range(60)]
    assert ResidualModel.fit(rows, [0.0] * 60) is None


def test_residual_model_learns_a_seasonal_effect():
    # Phần dư +100 vào nửa cuối năm, -100 nửa đầu: chỉ đặc trưng "month" mới nói được điều đó.
    rows = [[0.0, 0.0, 0.0, 0.0, float(m)] for m in (list(range(1, 13)) * 6)]
    residuals = [100.0 if r[4] >= 7 else -100.0 for r in rows]
    model = ResidualModel.fit(rows, residuals)
    assert model.predict([0.0, 0.0, 0.0, 0.0, 10.0]) > 20
    assert model.predict([0.0, 0.0, 0.0, 0.0, 3.0]) < -20


def test_fit_is_deterministic():
    rows = build_rows(make_periods(80), "lagged")
    a = predict(fit(rows), rows[-1].features)
    b = predict(fit(rows), rows[-1].features)
    assert a == b


def test_fit_needs_the_minimum_number_of_labelled_rows():
    rows = build_rows(make_periods(MIN_TRAIN_ROWS), "lagged")  # chỉ có n-1 dòng dùng được
    assert fit(rows) is None


def test_prediction_is_rule_plus_residual():
    periods = make_periods(80)
    fitted = fit(build_rows(periods, "lagged"))
    row = build_row(periods, len(periods), "lagged", at=periods[-1].at + timedelta(days=7))
    p = predict(fitted, row.features)
    assert p.delta == pytest.approx(p.rule_delta + p.residual_delta)
    assert math.isfinite(p.delta)
