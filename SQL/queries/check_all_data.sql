-- Sanity check: every jurisdiction with its county, rates, source, footnotes and aliases.
-- LEFT JOINs keep jurisdictions that have no rates/footnotes/aliases (they show NULLs).
SELECT j.id            AS jurisdiction_id,
       j.name          AS jurisdiction,
       j.type,
       j.tax_code,
       c.name          AS county,
       r.id            AS rate_id,
       r.state_rate,
       r.local_rate,
       r.mctd_rate,
       r.effective_date,
       r.expiration_date,
       s.publication   AS source,
       s.edition_date,
       f.footnote_symbol,
       f.footnote,
       a.alias
FROM sales_tax.jurisdictions j
LEFT JOIN sales_tax.jurisdictions        c  ON c.id = j.county_id
LEFT JOIN sales_tax.rates                r  ON r.jurisdiction_id = j.id
LEFT JOIN sales_tax.data_sources_logging s  ON s.id = r.source_id
LEFT JOIN sales_tax.rates_footnotes      rf ON rf.rate_id = r.id
LEFT JOIN sales_tax.footnotes            f  ON f.id = rf.footnote_id
LEFT JOIN sales_tax.aliases              a  ON a.jurisdiction_id = j.id
ORDER BY j.type, j.name, r.effective_date;
