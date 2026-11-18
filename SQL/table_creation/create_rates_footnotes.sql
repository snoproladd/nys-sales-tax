/* Creates rates_footnotes table
    This table is to handle the multiple footnotes for a single rate in the rates table
    Schema to be id, rate_id, footnote_id */

CREATE TABLE
    sales_tax.rates_footnotes (
        id SERIAL PRIMARY KEY,
        rate_id INTEGER REFERENCES sales_tax.rates(id) NOT NULL,
        footnote_id INTEGER REFERENCES sales_tax.footnotes(id) NOT NULL,
        CONSTRAINT unique_rate_footnote UNIQUE (rate_id, footnote_id)
    );