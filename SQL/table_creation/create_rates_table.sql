/* Creates rates table
This table is used to store tax rates of NYS
Schema to be id, jurisdiction_id, state_rate, local_rate, effective_date, expiration_date, mctd_rate, source_id */

CREATE TABLE
    sales_tax.rates (
        id SERIAL PRIMARY KEY,
        jurisdiction_id INTEGER REFERENCES sales_tax.jurisdictions(id) NOT NULL,
        state_rate DECIMAL(10, 5) NOT NULL,
        local_rate DECIMAL(10, 5) NOT NULL,
        effective_date DATE  NOT NULL,
        expiration_date DATE,
        mctd_rate DECIMAL(10, 5) NOT NULL DEFAULT 0.00000,
        CONSTRAINT unique_rate UNIQUE (jurisdiction_id, effective_date),
        source_id INTEGER REFERENCES sales_tax.data_sources_logging(id) NOT NULL
    );
