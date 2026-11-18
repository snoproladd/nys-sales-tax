/* rebuild_all.sql - full rebuild of the sales_tax schema with seed test data.
   Run in a Query Tool connected to the nys_tax database as postgres.
   Order: schema -> drops -> creates (build order) -> app role + grants -> seed data.
   Safe to run repeatedly. Before the first run, replace CHANGE_ME with the app role's password
   (and put a placeholder back before committing). */

CREATE SCHEMA IF NOT EXISTS sales_tax;

-- ===== App role and default privileges (must come before the creates so new tables get the grants) =====
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'salestax_app') THEN
    CREATE ROLE salestax_app LOGIN PASSWORD 'CHANGE_ME';
  END IF;
END
$$;

GRANT CONNECT ON DATABASE nys_tax TO salestax_app;
GRANT USAGE ON SCHEMA sales_tax TO salestax_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA sales_tax
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO salestax_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA sales_tax
  GRANT USAGE, SELECT ON SEQUENCES TO salestax_app;

-- ===== Drops (drop order) =====

DROP TABLE IF EXISTS sales_tax.aliases;
DROP TABLE IF EXISTS sales_tax.rates_footnotes;
DROP TABLE IF EXISTS sales_tax.rates;
DROP TABLE IF EXISTS sales_tax.footnotes;
DROP TABLE IF EXISTS sales_tax.jurisdictions;
DROP TABLE IF EXISTS sales_tax.data_sources_logging;
-- ===== Creates (build order: jurisdictions, footnotes, data_sources_logging, rates, rates_footnotes, aliases) =====
/* Creates jurisdictions table
This table is used to store tax jurisdictions of NYS
Schema to be id, name, type, tax_code, county_id */

CREATE TABLE
    sales_tax.jurisdictions (
        id SERIAL PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        type text CHECK (type IN ('County', 'City', 'Town_or_Village')) NOT NULL,
        tax_code VARCHAR(50),
        county_id INTEGER REFERENCES sales_tax.jurisdictions(id),
        CONSTRAINT unique_jurisdiction UNIQUE NULLS NOT DISTINCT(name, type, county_id)
        );

    

/* Creates footnotes table
This table is used to store footnotes of NYS tax rates
Schema to be ID, FOOTNOTE_SYMBOL,  FOOTNOTE */

CREATE TABLE
    sales_tax.footnotes (
        id SERIAL PRIMARY KEY,
        footnote_symbol VARCHAR(50) UNIQUE NOT NULL,
        footnote TEXT NOT NULL
    );
     

/* Creates data sources logging table
 This table is used to log data sources for tax rates of NYS
 Schema to be publication, edition_date, loaded_date
 links to rates.source_id column
 */

CREATE TABLE
    sales_tax.data_sources_logging (
        id SERIAL PRIMARY KEY,
        publication VARCHAR(255) NOT NULL,
        edition_date DATE NOT NULL,
        loaded_date TIMESTAMP NOT NULL DEFAULT NOW(),
        CONSTRAINT unique_data_source UNIQUE (publication, edition_date)
    );

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

/* Creates aliases table
This table is used to store aliases of NYS tax jurisdictions
Schema to be ID, JURISDICTION_ID, ALIAS */

CREATE TABLE
    sales_tax.aliases (
        id SERIAL PRIMARY KEY,
        jurisdiction_id INTEGER REFERENCES sales_tax.jurisdictions(id) NOT NULL,
        alias VARCHAR(255) UNIQUE NOT NULL
    );



-- ===== Seed test data =====

INSERT INTO sales_tax.jurisdictions (name, type, tax_code, county_id)
VALUES ('Madison', 'County', '2511', NULL),
       ('Oneida',  'County', '3010', NULL);

INSERT INTO sales_tax.jurisdictions (name, type, tax_code, county_id)
VALUES ('Oneida', 'City', '2541', (SELECT id FROM sales_tax.jurisdictions WHERE name = 'Madison' AND type = 'County')),
       ('Rome',   'City', '3015', (SELECT id FROM sales_tax.jurisdictions WHERE name = 'Oneida'  AND type = 'County'));

INSERT INTO sales_tax.data_sources_logging (publication, edition_date)
VALUES ('718-A', '2025-12-01');

INSERT INTO sales_tax.rates (jurisdiction_id, state_rate, local_rate, effective_date, expiration_date, mctd_rate, source_id)
VALUES ((SELECT id FROM sales_tax.jurisdictions WHERE name = 'Oneida' AND type = 'County'),
        4, 5.00, '2006-09-01', '2007-11-30', 0,
        (SELECT id FROM sales_tax.data_sources_logging WHERE publication = '718-A' AND edition_date = '2025-12-01')),
       ((SELECT id FROM sales_tax.jurisdictions WHERE name = 'Oneida' AND type = 'County'),
        4, 4.75, '2007-12-01', NULL, 0,
        (SELECT id FROM sales_tax.data_sources_logging WHERE publication = '718-A' AND edition_date = '2025-12-01'));

INSERT INTO sales_tax.footnotes (footnote_symbol, footnote)
VALUES ('C', 'Additional rate will expire effective 30 Nov. 2027.');

INSERT INTO sales_tax.rates_footnotes (rate_id, footnote_id)
VALUES ((SELECT r.id FROM sales_tax.rates r
           JOIN sales_tax.jurisdictions j ON j.id = r.jurisdiction_id
          WHERE j.name = 'Oneida' AND j.type = 'County' AND r.effective_date = '2007-12-01'),
        (SELECT id FROM sales_tax.footnotes WHERE footnote_symbol = 'C'));
