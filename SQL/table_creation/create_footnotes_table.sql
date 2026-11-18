/* Creates footnotes table
This table is used to store footnotes of NYS tax rates
Schema to be ID, FOOTNOTE_SYMBOL,  FOOTNOTE */

CREATE TABLE
    sales_tax.footnotes (
        id SERIAL PRIMARY KEY,
        footnote_symbol VARCHAR(50) UNIQUE NOT NULL,
        footnote TEXT NOT NULL
    );
     