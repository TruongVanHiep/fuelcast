-- FuelCast — schema lõi
-- Tương ứng phần "Các bảng lõi" trong tài liệu thiết kế.

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ─────────────────────────── DANH MỤC ───────────────────────────

CREATE TABLE fuel_product (
    id            SMALLSERIAL PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL UNIQUE,
    name_vi       VARCHAR(100) NOT NULL,
    unit          VARCHAR(10)  NOT NULL CHECK (unit IN ('lít', 'kg')),
    is_gasoline   BOOLEAN      NOT NULL,
    display_order SMALLINT     NOT NULL
);

COMMENT ON COLUMN fuel_product.is_gasoline IS 'Quyết định có áp thuế tiêu thụ đặc biệt hay không';

-- Ai công bố giá. Hôm nay là Bộ Công Thương; dự thảo nghị định mới có thể
-- chuyển sang doanh nghiệp tự công bố, khi đó chỉ cần thêm dòng vào bảng này.
CREATE TABLE price_publisher (
    id      SMALLSERIAL PRIMARY KEY,
    code    VARCHAR(30)  NOT NULL UNIQUE,
    name_vi VARCHAR(150) NOT NULL,
    type    VARCHAR(20)  NOT NULL CHECK (type IN ('REGULATOR', 'TRADER')),
    website VARCHAR(255)
);

-- ──────────────────────── KỲ ĐIỀU HÀNH ─────────────────────────

CREATE TABLE adjustment_cycle (
    id             BIGSERIAL PRIMARY KEY,
    cycle_start    DATE        NOT NULL,
    cycle_end      DATE        NOT NULL,
    announced_at   TIMESTAMPTZ,
    effective_from TIMESTAMPTZ,
    document_no    VARCHAR(50),
    source_url     VARCHAR(500),
    status         VARCHAR(20) NOT NULL
                   CHECK (status IN ('OPEN', 'ANNOUNCED', 'SETTLED')),
    CONSTRAINT uq_cycle_range UNIQUE (cycle_start, cycle_end),
    CONSTRAINT ck_cycle_order CHECK (cycle_end >= cycle_start)
);

CREATE INDEX ix_cycle_status ON adjustment_cycle (status, cycle_end DESC);

-- ───────────────────── DỮ LIỆU GIÁ (time-series) ────────────────

CREATE TABLE retail_price (
    observed_at  TIMESTAMPTZ   NOT NULL,
    product_id   SMALLINT      NOT NULL REFERENCES fuel_product (id),
    publisher_id SMALLINT      NOT NULL REFERENCES price_publisher (id),
    region       SMALLINT      NOT NULL DEFAULT 1 CHECK (region IN (1, 2)),
    price_vnd    NUMERIC(12,2) NOT NULL CHECK (price_vnd > 0),
    delta_vnd    NUMERIC(12,2),
    cycle_id     BIGINT        REFERENCES adjustment_cycle (id),
    PRIMARY KEY (observed_at, product_id, publisher_id, region)
);

SELECT create_hypertable('retail_price', 'observed_at');
CREATE INDEX ix_retail_product_time ON retail_price (product_id, observed_at DESC);

CREATE TABLE world_price (
    observed_at TIMESTAMPTZ   NOT NULL,
    symbol      VARCHAR(30)   NOT NULL,
    price_usd   NUMERIC(12,4) NOT NULL,
    unit        VARCHAR(12)   NOT NULL,
    source      VARCHAR(40)   NOT NULL,
    PRIMARY KEY (observed_at, symbol)
);

SELECT create_hypertable('world_price', 'observed_at');

CREATE TABLE fx_rate (
    observed_at TIMESTAMPTZ   NOT NULL,
    pair        VARCHAR(10)   NOT NULL DEFAULT 'USDVND',
    buy_rate    NUMERIC(12,2),
    sell_rate   NUMERIC(12,2) NOT NULL,
    source      VARCHAR(40)   NOT NULL,
    PRIMARY KEY (observed_at, pair, source)
);

SELECT create_hypertable('fx_rate', 'observed_at');

-- ─────────────────── PHÂN RÃ GIÁ CƠ SỞ ──────────────────────────

CREATE TABLE base_price_component (
    id                   BIGSERIAL PRIMARY KEY,
    cycle_id             BIGINT   NOT NULL REFERENCES adjustment_cycle (id),
    product_id           SMALLINT NOT NULL REFERENCES fuel_product (id),
    platts_avg_usd       NUMERIC(12,4),
    premium_usd          NUMERIC(12,4),
    import_tax_pct       NUMERIC(6,3),
    excise_tax_pct       NUMERIC(6,3),
    env_tax_vnd          NUMERIC(10,2),
    vat_pct              NUMERIC(6,3),
    business_cost_vnd    NUMERIC(10,2),
    standard_profit_vnd  NUMERIC(10,2),
    bog_contribution_vnd NUMERIC(10,2),
    computed_base_vnd    NUMERIC(12,2),
    CONSTRAINT uq_component UNIQUE (cycle_id, product_id)
);

-- ───────────────────── DỰ BÁO & CHẤM ĐIỂM ───────────────────────

CREATE TABLE forecast (
    id              BIGSERIAL PRIMARY KEY,
    cycle_id        BIGINT        NOT NULL REFERENCES adjustment_cycle (id),
    product_id      SMALLINT      NOT NULL REFERENCES fuel_product (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    horizon_days    SMALLINT      NOT NULL,
    predicted_vnd   NUMERIC(12,2) NOT NULL,
    predicted_delta NUMERIC(12,2) NOT NULL,
    direction       VARCHAR(10)   NOT NULL CHECK (direction IN ('UP', 'DOWN', 'FLAT')),
    lower_vnd       NUMERIC(12,2),
    upper_vnd       NUMERIC(12,2),
    confidence      NUMERIC(4,3),
    model_version   VARCHAR(40)   NOT NULL,
    features        JSONB         NOT NULL,
    explanation     JSONB
);

CREATE INDEX ix_forecast_cycle ON forecast (cycle_id, product_id, created_at DESC);

-- Dự báo phải bất biến, nếu không thì bảng điểm độ chính xác vô nghĩa.
CREATE OR REPLACE FUNCTION forbid_forecast_update() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'forecast là bảng chỉ ghi thêm: không được sửa dòng đã tạo (id=%)', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_forecast_immutable
    BEFORE UPDATE ON forecast
    FOR EACH ROW EXECUTE FUNCTION forbid_forecast_update();

CREATE TABLE forecast_evaluation (
    forecast_id   BIGINT PRIMARY KEY REFERENCES forecast (id),
    actual_vnd    NUMERIC(12,2) NOT NULL,
    abs_error     NUMERIC(12,2) NOT NULL,
    pct_error     NUMERIC(8,4)  NOT NULL,
    direction_hit BOOLEAN       NOT NULL,
    evaluated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ──────────────────────── VẬN HÀNH ──────────────────────────────

CREATE TABLE ingestion_run (
    id            BIGSERIAL PRIMARY KEY,
    source        VARCHAR(40) NOT NULL,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    rows_ingested INTEGER     NOT NULL DEFAULT 0,
    target_url    VARCHAR(500),
    error_message TEXT,
    raw_payload   TEXT
);

CREATE INDEX ix_ingestion_recent ON ingestion_run (source, started_at DESC);
