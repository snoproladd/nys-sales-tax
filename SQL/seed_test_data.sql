-- Seed test data: Oneida name collision + one real rate change (Oneida County, 2007-12-01)
-- Run after drop_script.sql and the create scripts.

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
