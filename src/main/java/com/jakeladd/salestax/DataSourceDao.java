package com.jakeladd.salestax;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class DataSourceDao {
    public static String latestEdition(String publication) throws IOException, SQLException {
        String sql = "SELECT publication, edition_date "
                + "FROM data_sources_logging "
                + "WHERE publication = ? "
                + "ORDER BY edition_date DESC "
                + "FETCH FIRST 1 ROWS ONLY";

        try (Connection conn = ConnectionFactory.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, publication);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return "Pub " + rs.getString("publication")
                            + " (" + rs.getObject("edition_date", LocalDate.class) + " edition)";
                }
            }

            return "unknown edition";
        }
    }
}
