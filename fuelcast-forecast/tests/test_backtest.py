from dataclasses import replace

import pytest

from app.backtest.runner import summarise, walk_forward
from tests.conftest import make_periods

K = 55  # kỳ được thử "nhìn lén tương lai"


@pytest.mark.parametrize("mode", ["lagged", "same_period"])
def test_future_data_never_changes_an_earlier_prediction(mode):
    """Chống leakage: hỏng bất cứ thứ gì SAU kỳ K thì các lần đoán tới kỳ K không được đổi."""
    periods = make_periods(80, truth=mode)
    baseline = {s.index: s.combined for s in walk_forward(periods, mode) if s.index <= K}

    tampered = periods[: K + 1] + [
        replace(p, delta_vnd=1e6, delta_usd=1e3) for p in periods[K + 1 :]
    ]
    after = {s.index: s.combined for s in walk_forward(tampered, mode) if s.index <= K}

    assert after == baseline


def test_first_step_uses_exactly_min_train_rows():
    steps = walk_forward(make_periods(60), "lagged", min_train=20)
    assert steps[0].train_rows == 20
    assert [s.train_rows for s in steps][:3] == [20, 21, 22]


def test_finds_a_real_lagged_relationship():
    summary = summarise(walk_forward(make_periods(120, truth="lagged"), "lagged"))
    assert summary.gain_rule > 0.5  # tín hiệu mạnh, phải bắt được
    assert summary.hit_rule > 0.85


def test_same_period_truth_is_explained_but_not_predictable_in_advance():
    """Cái bẫy của dự án gói trong một test: cùng dữ liệu, đoán bằng giá kỳ trước thì vô nghĩa,
    còn dùng giá của chính kỳ đó thì "chính xác" một cách giả tạo."""
    periods = make_periods(120, truth="same_period")
    explained = summarise(walk_forward(periods, "same_period"))
    forecast = summarise(walk_forward(periods, "lagged"))
    assert explained.gain_rule > 0.5
    assert forecast.gain_rule < 0.15


def test_does_not_invent_skill_from_pure_noise():
    periods = make_periods(120, truth="none")
    summary = summarise(walk_forward(periods, "lagged"))
    assert summary.gain_combined < 0.10
    assert summary.gain_rule < 0.10


def test_empty_summary_is_all_zero():
    summary = summarise([])
    assert summary.n == 0
    assert summary.gain_combined == 0.0
