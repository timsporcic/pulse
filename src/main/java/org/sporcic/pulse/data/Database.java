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

/**
 * Opens the SQLite database with the pragmas this app depends on and applies
 * {@code db/schema.sql} (idempotent {@code CREATE ... IF NOT EXISTS}, so the
 * same file serves jOOQ codegen at build time and migration at runtime).
 *
 * <p>Multiple DataSources may point at the same file within this one process
 * (the web app and JobRunr each open their own); WAL plus IMMEDIATE
 * transactions make that safe. Never share the file across processes.
 */
public final class Database {

    private Database() {}

    /** Opens (creating if needed) the SQLite database and applies db/schema.sql. */
    public static DSLContext open(Path file) {
        return DSL.using(dataSource(file), SQLDialect.SQLITE);
    }

    /**
     * The configured DataSource with schema applied. Every connection it hands
     * out has WAL journaling, a 5s busy timeout, enforced foreign keys, and
     * {@code BEGIN IMMEDIATE} transactions - see the inline comment for why
     * the last one is load-bearing.
     */
    public static SQLiteDataSource dataSource(Path file) {
        var config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
        config.enforceForeignKeys(true);
        // BEGIN IMMEDIATE: take the write lock up front so concurrent
        // transactions queue on busy_timeout instead of failing with
        // SQLITE_BUSY_SNAPSHOT on read->write upgrades (JobRunr does those)
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + file);

        applySchema(dataSource);
        return dataSource;
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
