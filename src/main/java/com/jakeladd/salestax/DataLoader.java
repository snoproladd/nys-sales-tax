package com.jakeladd.salestax;

import com.jakeladd.salestax.LoadPlan.SourceRow;
import com.jakeladd.salestax.LoadPlan.FootnoteRow;
import com.jakeladd.salestax.LoadPlan.JurisdictionKey;
import com.jakeladd.salestax.LoadPlan.JurisdictionRow;
import com.jakeladd.salestax.LoadPlan.RateRow;
import com.jakeladd.salestax.LoadPlan.AliasRow;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;
import java.sql.Types;

public class DataLoader {
    public static void main(String[] args) throws Exception {
        LoadPlan plan = LoadPlanner.build(Path.of("data"));

        try (Connection conn = ConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                deleteAll(conn);
                Map<SourceRow, Integer> sourceIds = insertSources(conn, plan);
                Map<String, Integer> footnoteIds = insertFootnotes(conn, plan);
                Map<JurisdictionKey, Integer> jurisdictionIds = insertJurisdictions(conn, plan);
                insertRates(conn, plan, jurisdictionIds, sourceIds, footnoteIds);
                insertAliases(conn, plan, jurisdictionIds);

                conn.commit();
                System.out.println("Load complete.");
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Load failed, nothing changed: " + e.getMessage());
                throw e;
            }
        }
    }

    private static void deleteAll(Connection conn) throws SQLException {
        String[] deleteRowsSql = {
                "DELETE from aliases",
                "DELETE from rates_footnotes",
                "DELETE from rates",
                "DELETE from footnotes",
                "DELETE from jurisdictions",
                "DELETE from data_sources_logging" };
        try (Statement stmt = conn.createStatement()) {
            for (String sql : deleteRowsSql) {
                stmt.addBatch(sql);
                stmt.executeBatch();
            }
        }
    }

    private static Map<SourceRow, Integer> insertSources(Connection conn, LoadPlan plan) throws SQLException {
        String sql = "INSERT INTO data_sources_logging (publication, edition_date) VALUES (?, ?)";
        Map<SourceRow, Integer> ids = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (SourceRow s : plan.sources()) {
                stmt.setString(1, s.publication());
                stmt.setObject(2, s.editionDate());
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    keys.next();
                    ids.put(s, keys.getInt(1));
                }
            }
        }
        return ids;
    }

    private static Map<String, Integer> insertFootnotes(Connection conn, LoadPlan plan) throws SQLException {
        String sql = "INSERT INTO footnotes (footnote_symbol, footnote) VALUES (?, ?)";
        Map<String, Integer> footnoteIds = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (FootnoteRow f : plan.footnotes()) {
                stmt.setString(1, f.symbol());
                stmt.setString(2, f.text());
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    keys.next();
                    footnoteIds.put(f.symbol(), keys.getInt(1));
                }
            }
        }

        return footnoteIds;
    }

    private static Map<JurisdictionKey, Integer> insertJurisdictions(Connection conn, LoadPlan plan)
            throws SQLException {
        String sql = "INSERT INTO jurisdictions (name, type, tax_code, county_id) VALUES (?, ?, ?, ?)";
        Map<JurisdictionKey, Integer> jurisdictionIds = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (JurisdictionRow j : plan.jurisdictions()) {
                stmt.setString(1, j.key().name());
                stmt.setString(2, j.key().type());
                stmt.setString(3, j.taxCode());
                if (j.countyKey() == null) {
                    stmt.setNull(4, Types.INTEGER);
                } else {
                    stmt.setInt(4, jurisdictionIds.get(j.countyKey()));
                }
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    keys.next();
                    jurisdictionIds.put(j.key(), keys.getInt(1));
                }
            }
        }

        return jurisdictionIds;

    }

    private static void insertRates(Connection conn, LoadPlan plan,
            Map<JurisdictionKey, Integer> jurisdictionIds,
            Map<SourceRow, Integer> sourceIds,
            Map<String, Integer> footnoteIds) throws SQLException {

        String rateSql = "INSERT INTO rates (jurisdiction_id, state_rate, local_rate, effective_date, "
                + "expiration_date, mctd_rate, source_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String linkSql = "INSERT INTO rates_footnotes (rate_id, footnote_id) VALUES (?, ?)";

        try (PreparedStatement rateStmt = conn.prepareStatement(rateSql, Statement.RETURN_GENERATED_KEYS);
                PreparedStatement linkStmt = conn.prepareStatement(linkSql)) {

            for (RateRow r : plan.rates()) {
                rateStmt.setInt(1, jurisdictionIds.get(r.jurisdiction()));
                rateStmt.setBigDecimal(2, r.stateRate());
                rateStmt.setBigDecimal(3, r.localRate());
                rateStmt.setObject(4, r.effectiveDate());
                if (r.expirationDate() == null) {
                    rateStmt.setNull(5, Types.DATE);
                } else {
                    rateStmt.setObject(5, r.expirationDate());
                }
                rateStmt.setBigDecimal(6, r.mctdRate());
                rateStmt.setInt(7, sourceIds.get(r.source()));

                rateStmt.executeUpdate();

                try (ResultSet keys = rateStmt.getGeneratedKeys()) {
                    keys.next();
                    int rateId = keys.getInt(1);

                    for (String symbol : r.footnoteSymbols()) {
                        linkStmt.setInt(1, rateId);
                        linkStmt.setInt(2, footnoteIds.get(symbol));
                        linkStmt.executeUpdate();

                    }
                }
            }

        }
    }

    private static void insertAliases(Connection conn, LoadPlan plan, Map<JurisdictionKey, Integer> jurisdictionIds) throws SQLException {
        String sql = "INSERT INTO aliases (jurisdiction_id, alias) VALUES (?, ?)";

        try(PreparedStatement stmt = conn.prepareStatement(sql)){
            for (AliasRow a : plan.aliases()){
                stmt.setInt(1, jurisdictionIds.get(a.jurisdiction()));
                stmt.setString(2, a.alias());
                stmt.executeUpdate();
            }}}
            

        
    }

