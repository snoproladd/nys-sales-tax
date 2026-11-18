package com.jakeladd.salestax;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

public class JurisdictionDao {
    public static List<Jurisdiction> findByName(String name) throws IOException, SQLException {
        String sql = "SELECT j.id, j.name, j.type, j.tax_code, j.county_id,"
                + " c.name AS county_name"
                + " FROM jurisdictions j"
                + " LEFT JOIN jurisdictions c ON c.id = j.county_id"
                + " WHERE LOWER(j.name) = LOWER(?)";
        List<Jurisdiction> results = new ArrayList<>();

        try (Connection conn = ConnectionFactory.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Jurisdiction jurisdiction = new Jurisdiction(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getString("tax_code"),
                            (Integer) rs.getObject("county_id", Integer.class),
                            rs.getString("county_name"));
                    results.add(jurisdiction);
                }
            }
        }

        return results;
    }
}
