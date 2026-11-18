package com.jakeladd.salestax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TaxResult (
    BigDecimal combinedRate,
    BigDecimal tax,
    BigDecimal total,
    LocalDate effectiveDate,
    String note,
    List <String> footnotes){};