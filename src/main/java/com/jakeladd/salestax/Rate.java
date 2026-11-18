package com.jakeladd.salestax;
import java.math.BigDecimal;
import java.time.LocalDate;

public record Rate (
    int id,
    int jurisdiction_id,
    BigDecimal state_rate,
    BigDecimal local_rate,
    BigDecimal mctd_rate,
    LocalDate effective_date,
    LocalDate expiration_date,
    int source_id
) {
}
