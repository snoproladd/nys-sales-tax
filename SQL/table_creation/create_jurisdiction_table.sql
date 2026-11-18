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

    