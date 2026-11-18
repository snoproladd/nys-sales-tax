/* Creates aliases table
This table is used to store aliases of NYS tax jurisdictions
Schema to be ID, JURISDICTION_ID, ALIAS */

CREATE TABLE
    sales_tax.aliases (
        id SERIAL PRIMARY KEY,
        jurisdiction_id INTEGER REFERENCES sales_tax.jurisdictions(id) NOT NULL,
        alias VARCHAR(255) UNIQUE NOT NULL
    );

