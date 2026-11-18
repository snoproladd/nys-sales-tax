package com.jakeladd.salestax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.io.IOException;
import java.sql.SQLException;

public class TaxCalculator {
    static BigDecimal combinedRate(Rate rate) {
        BigDecimal rawRate = rate.state_rate().add(rate.local_rate()).add(rate.mctd_rate());
        return rawRate;
    }

    static Optional<TaxResult> calculate(Jurisdiction j, BigDecimal amount, LocalDate date)
            throws IOException, SQLException {
        List<Rate> rates;
        String note = "";
        switch (j.type()) {
            case "Town_or_Village":
                rates = RateDao.findRateOn(j.countyId(), date);
                note = "Jurisdiction of type: <Town or Village> expressed as County tax rate.";
                break;
            case "City":
                rates = RateDao.findRateOn(j.id(), date);
                if (rates.isEmpty() && j.countyId() != null) {
                    rates = RateDao.findRateOn(j.countyId(), date);
                    note = "County rate applies.";
                } else {
                    note = "City rate applies.";
                }
                break;
            case "County":
                rates = RateDao.findRateOn(j.id(), date);
                note = "County rate applies.";
                break;
            default:
                rates = List.of();
                note = "No rate found";

        }
        if (rates.isEmpty()) {
            return Optional.empty();
        }
        Rate rate = rates.get(0);
        List<String> footnotes = FootnoteDao.findForRate(rate.id());
        BigDecimal combinedRate = combinedRate(rate);


        

        BigDecimal taxAmount = taxOwed(amount, combinedRate);

        BigDecimal total = amount.add(taxAmount);

        return Optional.of(new TaxResult(combinedRate, taxAmount, total, rate.effective_date(), note, footnotes));
    }
    
        static BigDecimal taxOwed(BigDecimal amount, BigDecimal ratePct){
            return amount.multiply(ratePct)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
}
