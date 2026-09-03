package org.sporcic.pulse.data;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;

public final class Database {

    private Database() {}

    /** Opens (creating if needed) the SQLite database and applies db/schema.sql. */
    public static DSLContext open(Path file) {
        var config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
        config.enforceForeignKeys(true);

        var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + file);

        applySchema(dataSource);
        return DSL.using(dataSource, SQLDialect.SQLITE);
    }

    private static void applySchema(SQLiteDataSource dataSource) {
        // sqlite-jdbc rejects comment-only statements, so strip comments first
        var schema = schemaSql()
                .replaceAll("(?m)--.*$", "")
                .replaceAll("(?s)/\\*.*?\\*/", "");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            for (var sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not apply db/schema.sql", e);
        }
    }

    private static String schemaSql() {
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("db/schema.sql not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
