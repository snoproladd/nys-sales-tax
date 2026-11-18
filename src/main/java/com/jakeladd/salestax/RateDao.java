package com.jakeladd.salestax;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

public class RateDao {
    public static List<Rate> findRateOn(int jurisdictionId, LocalDate date) throws IOException, SQLException {
        String sql = "SELECT id, jurisdiction_id, state_rate, local_rate, mctd_rate, effective_date, expiration_date, source_id "
                + "FROM rates WHERE jurisdiction_id = ? AND effective_date <= ? AND (expiration_date IS NULL OR ? <= expiration_date)";
        List<Rate> results = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, jurisdictionId);
            stmt.setDate(2, java.sql.Date.valueOf(date));
            stmt.setDate(3, java.sql.Date.valueOf(date));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Rate rate = new Rate(
                            rs.getInt("id"),
                            rs.getInt("jurisdiction_id"),
                            rs.getBigDecimal("state_rate"),
                            rs.getBigDecimal("local_rate"),
                            rs.getBigDecimal("mctd_rate"),
                            rs.getObject("effective_date", java.time.LocalDate.class),
                            rs.getObject("expiration_date", java.time.LocalDate.class),
                            rs.getInt("source_id")
                    );
                    results.add(rate);
                }
            }
        }

        return results;
    }
}
