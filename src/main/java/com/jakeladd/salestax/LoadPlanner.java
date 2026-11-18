package com.jakeladd.salestax;

import com.jakeladd.salestax.LoadPlan.AliasRow;
import com.jakeladd.salestax.LoadPlan.FootnoteRow;
import com.jakeladd.salestax.LoadPlan.JurisdictionKey;
import com.jakeladd.salestax.LoadPlan.JurisdictionRow;
import com.jakeladd.salestax.LoadPlan.RateRow;
import com.jakeladd.salestax.LoadPlan.SourceRow;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reads the CSVs in data/ and works out every row to insert. No database access.
 *
 * Rules:
 * - Counties: full history from Pub 718-A. 718-A gives the county's local rate only, so each
 *   period is split wherever the state rate or the MCTD surcharge changed, and each piece gets
 *   the state and MCTD rates in force at that time. Before a county's first local tax (but after
 *   the state tax began on 1965-08-01) a local rate of 0 is stored, so the state rate still applies.
 * - Cities: current rate only, from Pub 718 (combined rate). Its local share is today's
 *   combined - today's state - today's MCTD, held constant and split wherever the state or MCTD
 *   rate changed. It is dated from the later of the city's and its county's last
 *   change in 718-A; earlier dates fall back to the county rate (a documented approximation).
 *   Cities that have repealed their tax get a jurisdiction row but no rates.
 * - Expiration dates are inclusive: the next period's start date minus one day.
 */
public final class LoadPlanner {

    static final SourceRow PUB_718 = new SourceRow("718", LocalDate.of(2025, 3, 1));
    static final SourceRow PUB_718A = new SourceRow("718-A", LocalDate.of(2025, 12, 1));

    // MCTD surcharge (718-A footnote A): 1/4% from 1981-09-01, 3/8% from 2005-06-01
    private static final LocalDate MCTD_START = LocalDate.of(1981, 9, 1);
    private static final LocalDate MCTD_RAISE = LocalDate.of(2005, 6, 1);
    private static final BigDecimal MCTD_OLD = new BigDecimal("0.25");
    private static final BigDecimal MCTD_NEW = new BigDecimal("0.375");

    private LoadPlanner() {}

    public static LoadPlan build(Path dataDir) throws IOException {
        List<Map<String, String>> pub718 = readCsv(dataDir.resolve("pub718_rates.csv"));
        List<Map<String, String>> history = readCsv(dataDir.resolve("pub718a_history.csv"));
        List<Map<String, String>> footnoteCsv = readCsv(dataDir.resolve("pub718a_footnotes.csv"));
        List<Map<String, String>> aliasCsv = readCsv(dataDir.resolve("pub718_aliases.csv"));

        // ---- sources and footnotes ----
        List<SourceRow> sources = List.of(PUB_718, PUB_718A);
        List<FootnoteRow> footnotes = new ArrayList<>();
        for (Map<String, String> f : footnoteCsv) {
            footnotes.add(new FootnoteRow(f.get("footnote_symbol"), f.get("footnote")));
        }

        // ---- state rate history: effective date -> rate ----
        TreeSet<LocalDate> stateDates = new TreeSet<>();
        Map<LocalDate, BigDecimal> stateRates = new HashMap<>();
        for (Map<String, String> h : history) {
            if (h.get("jurisdiction_type").equals("State")) {
                LocalDate d = LocalDate.parse(h.get("effective_date"));
                stateDates.add(d);
                stateRates.put(d, new BigDecimal(h.get("rate")));
            }
        }
        LocalDate stateStart = stateDates.first();

        // ---- jurisdictions ----
        // counties first (from 718), then every city seen in 718 or 718-A
        Map<String, Map<String, String>> pub718ByKey = new HashMap<>();
        Map<JurisdictionKey, JurisdictionRow> jurisdictions = new LinkedHashMap<>();
        for (Map<String, String> p : pub718) {
            pub718ByKey.put(p.get("name") + "|" + p.get("type"), p);
            if (p.get("type").equals("County")) {
                JurisdictionKey key = new JurisdictionKey(p.get("name"), "County", null);
                jurisdictions.put(key, new JurisdictionRow(key, p.get("reporting_code")));
            }
        }
        for (Map<String, String> p : pub718) {
            if (p.get("type").equals("City")) {
                JurisdictionKey key = new JurisdictionKey(p.get("name"), "City", blankToNull(p.get("county")));
                jurisdictions.put(key, new JurisdictionRow(key, p.get("reporting_code")));
            }
        }
        for (Map<String, String> h : history) {
            if (h.get("jurisdiction_type").equals("City")) {
                JurisdictionKey key = new JurisdictionKey(
                        h.get("jurisdiction_name"), "City", blankToNull(h.get("county")));
                jurisdictions.putIfAbsent(key, new JurisdictionRow(key, null)); // repealed: no code
            }
        }

        // ---- rates ----
        List<RateRow> rates = new ArrayList<>();
        Map<String, LocalDate> lastChange = new HashMap<>(); // "name|type" -> latest 718-A date
        for (Map<String, String> h : history) {
            String k = h.get("jurisdiction_name") + "|" + h.get("jurisdiction_type");
            LocalDate d = LocalDate.parse(h.get("effective_date"));
            lastChange.merge(k, d, (a, b) -> a.isAfter(b) ? a : b);
        }

        // counties: full history
        for (JurisdictionRow j : jurisdictions.values()) {
            if (!j.key().type().equals("County")) {
                continue;
            }
            boolean mctd = pub718ByKey.get(j.key().name() + "|County").get("mctd").equals("true");

            List<Map<String, String>> periods = new ArrayList<>();
            for (Map<String, String> h : history) {
                if (h.get("jurisdiction_type").equals("County")
                        && h.get("jurisdiction_name").equals(j.key().name())
                        && h.get("event").equals("rate")) {
                    periods.add(h);
                }
            }
            periods.sort(Comparator.comparing(h -> LocalDate.parse(h.get("effective_date"))));

            // every date where the local, state or MCTD rate changes
            TreeSet<LocalDate> breaks = new TreeSet<>();
            breaks.add(stateStart);
            breaks.addAll(stateDates);
            if (mctd) {
                breaks.add(MCTD_START);
                breaks.add(MCTD_RAISE);
            }
            for (Map<String, String> p : periods) {
                breaks.add(LocalDate.parse(p.get("effective_date")));
            }

            List<LocalDate> starts = new ArrayList<>(breaks);
            for (int i = 0; i < starts.size(); i++) {
                LocalDate start = starts.get(i);
                LocalDate end = (i + 1 < starts.size()) ? starts.get(i + 1).minusDays(1) : null;

                Map<String, String> local = null; // latest 718-A period started on or before 'start'
                for (Map<String, String> p : periods) {
                    if (!LocalDate.parse(p.get("effective_date")).isAfter(start)) {
                        local = p;
                    }
                }
                BigDecimal localRate = (local == null) ? BigDecimal.ZERO : new BigDecimal(local.get("rate"));
                List<String> symbols = (local == null) ? List.of() : splitFootnotes(local.get("footnote"));

                rates.add(new RateRow(j.key(),
                        stateRate(stateDates, stateRates, start),
                        localRate,
                        mctdRate(mctd, start),
                        start, end, PUB_718A, symbols));
            }
        }

        // cities: current combined rate from 718
        for (JurisdictionRow j : jurisdictions.values()) {
            if (!j.key().type().equals("City")) {
                continue;
            }
            Map<String, String> p = pub718ByKey.get(j.key().name() + "|City");
            if (p == null) {
                continue; // repealed: no rates, lookups fall back to the county
            }
            boolean mctd = p.get("mctd").equals("true");
            LocalDate start = lastChange.get(j.key().name() + "|City");
            if (j.key().countyName() != null) {
                LocalDate countyChange = lastChange.get(j.key().countyName() + "|County");
                if (countyChange != null && countyChange.isAfter(start)) {
                    start = countyChange;
                }
            }
            List<String> symbols = latestCityFootnotes(history, j.key().name());

            // The city's own share: today's combined rate minus today's state and MCTD rates.
            LocalDate today = stateDates.last().isAfter(MCTD_RAISE) ? stateDates.last() : MCTD_RAISE;
            BigDecimal local = new BigDecimal(p.get("combined_rate"))
                    .subtract(stateRate(stateDates, stateRates, today))
                    .subtract(mctdRate(mctd, today));

            // Split wherever the state or MCTD rate changed after the row starts.
            TreeSet<LocalDate> breaks = new TreeSet<>();
            breaks.add(start);
            for (LocalDate d : stateDates) {
                if (d.isAfter(start)) {
                    breaks.add(d);
                }
            }
            if (mctd) {
                if (MCTD_START.isAfter(start)) {
                    breaks.add(MCTD_START);
                }
                if (MCTD_RAISE.isAfter(start)) {
                    breaks.add(MCTD_RAISE);
                }
            }
            List<LocalDate> starts = new ArrayList<>(breaks);
            for (int i = 0; i < starts.size(); i++) {
                LocalDate s = starts.get(i);
                LocalDate end = (i + 1 < starts.size()) ? starts.get(i + 1).minusDays(1) : null;
                rates.add(new RateRow(j.key(), stateRate(stateDates, stateRates, s), local,
                        mctdRate(mctd, s), s, end, PUB_718, symbols));
            }
        }

        // ---- aliases ----
        List<AliasRow> aliases = new ArrayList<>();
        for (Map<String, String> a : aliasCsv) {
            JurisdictionKey target = new JurisdictionKey(a.get("jurisdiction_name"), a.get("jurisdiction_type"), null);
            aliases.add(new AliasRow(a.get("alias"), target));
        }

        return new LoadPlan(sources, footnotes, new ArrayList<>(jurisdictions.values()), rates, aliases);
    }

    // ---------- helpers ----------

    private static BigDecimal stateRate(TreeSet<LocalDate> dates, Map<LocalDate, BigDecimal> rates, LocalDate on) {
        LocalDate d = dates.floor(on);
        return d == null ? BigDecimal.ZERO : rates.get(d);
    }

    private static BigDecimal mctdRate(boolean inMctd, LocalDate on) {
        if (!inMctd || on.isBefore(MCTD_START)) {
            return BigDecimal.ZERO;
        }
        return on.isBefore(MCTD_RAISE) ? MCTD_OLD : MCTD_NEW;
    }

    /** "c/d/e/q" -> [c, d, e, q]; "a*" -> [a, *]. Every symbol is one character. */
    static List<String> splitFootnotes(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (char ch : raw.toCharArray()) {
            if (ch != '/' && !Character.isWhitespace(ch)) {
                out.add(String.valueOf(ch));
            }
        }
        return out;
    }

    private static List<String> latestCityFootnotes(List<Map<String, String>> history, String city) {
        Map<String, String> latest = null;
        for (Map<String, String> h : history) {
            if (h.get("jurisdiction_type").equals("City") && h.get("jurisdiction_name").equals(city)
                    && h.get("event").equals("rate")) {
                if (latest == null || LocalDate.parse(h.get("effective_date"))
                        .isAfter(LocalDate.parse(latest.get("effective_date")))) {
                    latest = h;
                }
            }
        }
        return latest == null ? List.of() : splitFootnotes(latest.get("footnote"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Reads a CSV with a header row into one map per line. Handles "quoted, fields". */
    static List<Map<String, String>> readCsv(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> header = parseLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> values = parseLine(lines.get(i));
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < values.size() ? values.get(c) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
