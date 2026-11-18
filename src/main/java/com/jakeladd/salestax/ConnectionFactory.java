package com.jakeladd.salestax;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.IOException;

public class ConnectionFactory {
    public static Connection getConnection() throws IOException, SQLException {
        Properties props = new Properties();

        try (
                FileInputStream in = new FileInputStream("db.properties")) {
            props.load(in);
        }
        String url = props.getProperty("db.url");
        String username = props.getProperty("db.user");
        String password = props.getProperty("db.password");
        return DriverManager.getConnection(url, username, password);
    }
}
