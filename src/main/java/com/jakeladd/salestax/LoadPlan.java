package com.jakeladd.salestax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the loader needs to insert, already worked out from the CSVs.
 * Built by {@link LoadPlanner}; inserted by DataLoader.
 *
 * Lists are in a safe insert order: in {@code jurisdictions}, every county
 * comes before the cities that point at it.
 *
 * Rows refer to each other by key (name/type/county, publication/edition,
 * footnote symbol) rather than by database id, because ids don't exist until
 * the rows are inserted. Keys are records, so they work as HashMap keys.
 */
public record LoadPlan(
        List<SourceRow> sources,
        List<FootnoteRow> footnotes,
        List<JurisdictionRow> jurisdictions,
        List<RateRow> rates,
        List<AliasRow> aliases) {

    /** Identifies a jurisdiction: county is null for counties (and New York City). */
    public record JurisdictionKey(String name, String type, String countyName) {}

    /** A row for data_sources_logging. Also used as the key a rate points to. */
    public record SourceRow(String publication, LocalDate editionDate) {}

    /** A row for footnotes. */
    public record FootnoteRow(String symbol, String text) {}

    /** A row for jurisdictions. taxCode is null for cities that have repealed their tax. */
    public record JurisdictionRow(JurisdictionKey key, String taxCode) {
        /** The key of this jurisdiction's county, or null if it has none. */
        public JurisdictionKey countyKey() {
            return key.countyName() == null ? null
                    : new JurisdictionKey(key.countyName(), "County", null);
        }
    }

    /** A row for rates, plus the footnote symbols to link through rates_footnotes. */
    public record RateRow(
            JurisdictionKey jurisdiction,
            BigDecimal stateRate,
            BigDecimal localRate,
            BigDecimal mctdRate,
            LocalDate effectiveDate,
            LocalDate expirationDate,     // inclusive; null = still current
            SourceRow source,
            List<String> footnoteSymbols) {}

    /** A row for aliases. */
    public record AliasRow(String alias, JurisdictionKey jurisdiction) {}
}
