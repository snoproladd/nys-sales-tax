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