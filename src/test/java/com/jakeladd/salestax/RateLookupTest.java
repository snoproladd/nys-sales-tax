package com.jakeladd.salestax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Database tests against the full data set.
 * Requires Postgres running, SQL/rebuild_all.sql run, and DataLoader run.
 */
class RateLookupTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final LocalDate TODAY = LocalDate.now();

    // ---------- helpers (not tests) ----------

    private Jurisdiction find(String name, String type) throws Exception {
        for (Jurisdiction j : JurisdictionDao.findByName(name)) {
            if (j.type().equals(type)) {
                return j;
            }
        }
        throw new IllegalStateException("Data missing: " + name + " (" + type + ")");
    }

    private TaxResult calc(String name, String type, LocalDate date) throws Exception {
        Optional<TaxResult> result = TaxCalculator.calculate(find(name, type), HUNDRED, date);
        assertTrue(result.isPresent(), "expected a rate for " + name + " on " + date);
        return result.get();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    // ---------- jurisdiction search ----------

    @Test
    void unknownNameReturnsNoMatches() throws Exception {
        assertTrue(JurisdictionDao.findByName("xyz").isEmpty());
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        assertEquals(2, JurisdictionDao.findByName("ONEIDA").size());
    }

    @Test
    void oneidaMatchesBothCountyAndCity() throws Exception {
        List<Jurisdiction> matches = JurisdictionDao.findByName("oneida");
        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(j -> j.type().equals("County")));
        assertTrue(matches.stream().anyMatch(j -> j.type().equals("City")));
    }

    @Test
    void cityOfOneidaIsInMadisonCountyNotOneidaCounty() throws Exception {
        assertEquals("Madison", find("oneida", "City").countyName());
    }

    @Test
    void cityOfFultonIsInOswegoCountyNotFultonCounty() throws Exception {
        assertEquals("Oswego", find("fulton", "City").countyName());
    }

    // ---------- as-of date lookup: Oneida County changed rate on 2007-12-01 ----------

    @Test
    void rateBeforeChange() throws Exception {
        TaxResult r = calc("oneida", "County", LocalDate.of(2007, 11, 30));
        assertAmount("9.00", r.combinedRate());
        assertAmount("9.00", r.tax());
        assertEquals(LocalDate.of(2006, 9, 1), r.effectiveDate());
    }

    @Test
    void rateOnDayOfChange() throws Exception {
        TaxResult r = calc("oneida", "County", LocalDate.of(2007, 12, 1));
        assertAmount("8.75", r.combinedRate());
        assertAmount("8.75", r.tax());
        assertAmount("108.75", r.total());
        assertEquals(LocalDate.of(2007, 12, 1), r.effectiveDate());
    }

    @Test
    void currentRateHasNoExpirationDate() throws Exception {
        // Guards the "expiration_date IS NULL" branch of the query
        assertAmount("8.75", calc("oneida", "County", TODAY).combinedRate());
    }

    @Test
    void stateRateOnlyBeforeCountyTaxBegan() throws Exception {
        // Oneida County's first local tax was 1982-12-01; before that only the 4% state rate applied
        assertAmount("4", calc("oneida", "County", LocalDate.of(1975, 6, 1)).combinedRate());
    }

    @Test
    void stateRateChangeIsReflected() throws Exception {
        // The state rate was 4.25% from 2003-06-01 to 2005-05-31; Albany County local was 4%
        assertAmount("8.25", calc("albany", "County", LocalDate.of(2004, 1, 15)).combinedRate());
        assertAmount("8", calc("albany", "County", LocalDate.of(2005, 6, 1)).combinedRate());
    }

    @Test
    void noRateBeforeSalesTaxExisted() throws Exception {
        // NYS sales tax began 1965-08-01; also guards the AND/OR parentheses bug
        assertTrue(TaxCalculator.calculate(find("oneida", "County"), HUNDRED,
                LocalDate.of(1960, 1, 1)).isEmpty());
    }

    // ---------- current rates match Pub 718 ----------

    @Test
    void newYorkCityIncludesMctd() throws Exception {
        assertAmount("8.875", calc("new york city", "City", TODAY).combinedRate());
    }

    @Test
    void yonkersHasItsOwnHigherRate() throws Exception {
        assertAmount("8.875", calc("yonkers", "City", TODAY).combinedRate());
        assertAmount("8.375", calc("westchester", "County", TODAY).combinedRate());
    }

    // ---------- routing by jurisdiction type ----------

    @Test
    void cityWithOwnRateUsesIt() throws Exception {
        TaxResult r = calc("oneida", "City", TODAY);
        assertAmount("8", r.combinedRate());
        assertEquals("City rate applies.", r.note());
    }

    @Test
    void cityThatRepealedItsTaxFallsBackToCounty() throws Exception {
        // Elmira repealed its city tax in 1993; Chemung County's rate applies
        TaxResult r = calc("elmira", "City", TODAY);
        assertAmount("8", r.combinedRate());
        assertEquals("County rate applies.", r.note());
    }

    @Test
    void townOrVillageUsesItsCountyRate() throws Exception {
        // No towns in the data yet, so build one in memory pointing at Oneida County
        Jurisdiction oneidaCounty = find("oneida", "County");
        Jurisdiction town = new Jurisdiction(-1, "Testville", "Town_or_Village", "0000",
                oneidaCounty.id(), "Oneida");
        Optional<TaxResult> result = TaxCalculator.calculate(town, HUNDRED, LocalDate.of(2007, 12, 1));
        assertTrue(result.isPresent());
        assertAmount("8.75", result.get().combinedRate());
    }

    @Test
    void unknownTypeGivesEmpty() throws Exception {
        Jurisdiction odd = new Jurisdiction(-1, "Nowhere", "Hamlet", "0000", null, null);
        assertFalse(TaxCalculator.calculate(odd, HUNDRED, LocalDate.of(2007, 12, 1)).isPresent());
    }
    @Test
void schenectadyCountyHasTwoFootnotes() throws Exception {
    TaxResult r = calc("schenectady", "County", TODAY);
    r.footnotes().forEach(f -> System.out.println("Schenectady County -> " + f));

    assertEquals(2, r.footnotes().size());
    assertTrue(r.footnotes().stream().anyMatch(f -> f.startsWith("C")));
    assertTrue(r.footnotes().stream().anyMatch(f -> f.startsWith("E")));
}

@Test
void uticaHasCityAndCountySplitFootnotes() throws Exception {
    TaxResult r = calc("utica", "City", TODAY);
    r.footnotes().forEach(f -> System.out.println("Utica -> " + f));

    assertEquals(2, r.footnotes().size());
    assertTrue(r.footnotes().stream().anyMatch(f -> f.startsWith("a")));
    assertTrue(r.footnotes().stream().anyMatch(f -> f.startsWith("*")));
}
}
