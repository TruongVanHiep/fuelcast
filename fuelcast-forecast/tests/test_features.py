import math
from dataclasses import replace
from datetime import timedelta

import pytest

from app.features import build_row, build_rows
from tests.conftest import make_periods


def test_lagged_row_uses_previous_periods_world_price():
    periods = make_periods(10)
    row = build_row(periods, 5, "lagged")
    assert row.features[0] == periods[4].delta_usd  # usd_in: kỳ TRƯỚC
    assert row.features[1] == periods[3].delta_usd  # usd_in_prev
    assert row.features[2] == periods[4].delta_vnd  # vnd_prev
    assert row.features[3] == periods[3].delta_vnd  # vnd_prev2
    assert row.target == periods[5].delta_vnd


def test_same_period_row_uses_the_periods_own_world_price():
    periods = make_periods(10)
    row = build_row(periods, 5, "same_period")
    assert row.features[0] == periods[5].delta_usd  # usd_in: chính kỳ này
    assert row.features[1] == periods[4].delta_usd


def test_month_feature_comes_from_the_predicted_period():
    periods = make_periods(10)
    row = build_row(periods, 5, "lagged")
    assert row.features[4] == float(periods[5].at.month)


def test_future_row_has_no_target_and_takes_month_from_target_at():
    periods = make_periods(10)
    target_at = periods[-1].at + timedelta(days=7)
    row = build_row(periods, len(periods), "lagged", at=target_at)
    assert row.target is None
    assert row.features[0] == periods[-1].delta_usd
    assert row.features[4] == float(target_at.month)


def test_future_row_requires_a_timestamp():
    with pytest.raises(ValueError):
        build_row(make_periods(10), 10, "lagged")


def test_same_period_cannot_predict_a_future_period():
    periods = make_periods(10)
    with pytest.raises(ValueError):
        build_row(periods, len(periods), "same_period")


def test_gap_breaks_the_chain_in_lagged_mode():
    periods = make_periods(10)
    # Dịch từ kỳ 5 trở đi 20 ngày: kỳ 5 không còn liền kề kỳ 4.
    shifted = periods[:5] + [replace(p, at=p.at + timedelta(days=20)) for p in periods[5:]]
    assert build_row(shifted, 5, "lagged") is None
    assert build_row(shifted, 6, "lagged") is not None


def test_missing_lags_are_nan_not_zero():
    periods = make_periods(10)
    shifted = periods[:5] + [replace(p, at=p.at + timedelta(days=20)) for p in periods[5:]]
    row = build_row(shifted, 5, "same_period")  # same_period vẫn dùng được kỳ 5
    assert math.isnan(row.features[1])  # không có kỳ liền trước để lấy usd_in_prev
    assert math.isnan(row.features[2])  # vnd_prev
    assert math.isnan(row.features[3])  # vnd_prev2


def test_build_rows_skips_unusable_periods_only():
    periods = make_periods(10)
    assert [r.index for r in build_rows(periods, "lagged")] == list(range(1, 10))
    assert [r.index for r in build_rows(periods, "same_period")] == list(range(10))
