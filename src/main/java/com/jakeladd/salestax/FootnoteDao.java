package com.jakeladd.salestax;

import java.sql.Connection;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FootnoteDao {

    public static List<String> findForRate(int rateId) throws IOException, SQLException {
        String sql = "SELECT f.footnote_symbol, f.footnote "
                + "FROM rates_footnotes rf "
                + "JOIN footnotes f ON f.id = rf.footnote_id "
                + "WHERE rf.rate_id = ?";
        List<String> footnotes = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, rateId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String fnSymbol = rs.getString("footnote_symbol");
                    String fn = rs.getString("footnote");
                    footnotes.add(fnSymbol + ": " + fn);
                }
                return footnotes;
            }
        }
    }

}
