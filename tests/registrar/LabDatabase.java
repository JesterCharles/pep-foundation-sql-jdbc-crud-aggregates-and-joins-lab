package registrar;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Creates a new, uniquely named lab database on your local PostgreSQL server, gives every test a
 * fresh copy of the registrar tables, and drops that database again when the tests finish.
 *
 * Safety: each run invents a fresh name such as queries_lab_3f9c0a1b7d2e and creates it. If a
 * database with that name already exists, the run picks another name; it never drops a database it
 * did not create. While the run owns a database, its name is written to target/lab-database.txt,
 * so an interrupted run can be cleaned up by name.
 *
 * Connection: pgJDBC and psql both use the variables psql reads: PGHOST (a host name or IP
 * address, default localhost), PGPORT (default 5432), PGUSER (default: your login name) and
 * PGDATABASE (the existing database used to run CREATE DATABASE and DROP DATABASE, default
 * postgres). The password comes from your password file, or PGPASSWORD.
 */
final class LabDatabase {
    static final String PREFIX = "queries_lab_";
    static final Path RECORD = Path.of("target", "lab-database.txt");
    static final Path FIXTURES = Path.of("..", "fixtures");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUPLICATE_DATABASE = "42P04";

    private static String owned;

    private LabDatabase() {
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String host() throws SQLException {
        String host = env("PGHOST", "localhost");
        if (host.startsWith("/") || host.contains("\\") || host.contains(",")) {
            throw new SQLException("PGHOST is set to \"" + host + "\", which is not a host name. "
                    + "The tests connect over TCP, so set PGHOST to a host name or IP address such as localhost "
                    + "(see README.md, Setup).");
        }
        return host;
    }

    static String user() {
        return env("PGUSER", System.getProperty("user.name"));
    }

    private static Connection connect(String database) throws SQLException {
        String h = host();
        String url = "jdbc:postgresql://" + (h.contains(":") ? "[" + h + "]" : h) + ":" + env("PGPORT", "5432") + "/" + database;
        Properties props = new Properties();
        props.setProperty("user", user());
        String password = System.getenv("PGPASSWORD");
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        return DriverManager.getConnection(url, props);
    }

    private static void admin(String sql) throws SQLException {
        try (Connection c = connect(env("PGDATABASE", "postgres")); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    /** Creates a new lab database that this run owns and opens a connection to it. */
    static Connection create() throws SQLException, IOException {
        String name = null;
        for (int attempt = 0; attempt < 5 && name == null; attempt++) {
            byte[] bytes = new byte[6];
            RANDOM.nextBytes(bytes);
            String candidate = PREFIX + HexFormat.of().formatHex(bytes);
            try {
                admin("CREATE DATABASE " + candidate);
                name = candidate;
            } catch (SQLException e) {
                if (DUPLICATE_DATABASE.equals(e.getSQLState())) {
                    continue; // someone else's database has this name: leave it alone and try another
                }
                throw new SQLException("Could not reach PostgreSQL to create the lab database. "
                        + "Check that the server is running and that the connection check in README.md "
                        + "(Prerequisites) works with the same settings. Server said: " + e.getMessage(), e);
            }
        }
        if (name == null) {
            throw new SQLException("Could not find an unused name for the lab database; run the tests again.");
        }
        owned = name;
        Files.createDirectories(RECORD.getParent());
        Files.writeString(RECORD, name + System.lineSeparator());
        return connect(name);
    }

    static String ownedName() {
        return owned;
    }

    /** Closes the connection and drops only the database this run created. */
    static void drop(Connection c) throws SQLException, IOException {
        if (c != null) {
            c.close();
        }
        if (owned != null) {
            admin("DROP DATABASE IF EXISTS " + owned);
            owned = null;
            Files.deleteIfExists(RECORD);
        }
    }

    /** Replaces every table with empty ones from ../fixtures/schema.sql. */
    static void emptyTables(Connection c) throws SQLException, IOException {
        try (Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
            s.execute(Files.readString(FIXTURES.resolve("schema.sql")));
        }
    }

    /** Empty tables plus the registrar data that Task 1 seeds, loaded by the tests themselves. */
    static void registrarData(Connection c) throws SQLException, IOException {
        emptyTables(c);
        try (Statement s = c.createStatement()) {
            s.execute("INSERT INTO courses (code, title, capacity, prerequisite_code) VALUES "
                    + "('SQL101', 'SQL Foundations', 6, NULL), ('PGA110', 'PostgreSQL Admin Basics', 2, NULL), "
                    + "('SQL201', 'Reporting with SQL', 4, 'SQL101'), ('JDB301', 'JDBC Data Access', 4, 'SQL201')");
            s.execute("INSERT INTO students (id, name, email) SELECT g, 'Guest ' || g, 'guest' || g || '@registrar.test' "
                    + "FROM generate_series(901, 905) AS g");
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO students (id, name, email, city) VALUES (?, ?, ?, ?)")) {
            for (String[] f : csv("students.csv")) {
                p.setInt(1, Integer.parseInt(f[0]));
                p.setString(2, f[1]);
                p.setString(3, f[2]);
                p.setString(4, f[3].isEmpty() ? null : f[3]);
                p.executeUpdate();
            }
        }
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO enrollments (student_id, course_code, enrolled_at, grade) VALUES (?, ?, ?, ?)")) {
            for (String[] f : csv("enrollments.csv")) {
                p.setInt(1, Integer.parseInt(f[0]));
                p.setString(2, f[1]);
                p.setTimestamp(3, Timestamp.valueOf(f[2]));
                p.setBigDecimal(4, f[3].isEmpty() ? null : new BigDecimal(f[3]));
                p.executeUpdate();
            }
        }
    }

    private static List<String[]> csv(String name) throws IOException {
        List<String> lines = Files.readAllLines(FIXTURES.resolve(name), StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (!line.isBlank()) {
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    /** The learner's file, or null when it still holds only comments and blank lines. */
    static String learnerSql(String file) throws IOException {
        String text = Files.readString(Path.of(file));
        StringBuilder code = new StringBuilder();
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                code.append(line).append('\n');
            }
        }
        return code.isEmpty() ? null : text;
    }

    /** The file's SQL with block comments, -- comments and blank lines removed, for the few checks that read the text itself. */
    static String codeOnly(String file) throws IOException {
        StringBuilder code = new StringBuilder();
        String text = Files.readString(Path.of(file)).replaceAll("(?s)/\\*.*?\\*/", " ");
        for (String line : text.split("\\R")) {
            int comment = line.indexOf("--");
            String kept = (comment >= 0 ? line.substring(0, comment) : line).strip();
            if (!kept.isEmpty()) {
                code.append(kept).append('\n');
            }
        }
        return code.toString();
    }

    /** Runs one SELECT and returns its rows as text, with NULL written as "NULL". */
    static List<List<String>> rows(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return rows(r);
        }
    }

    static List<List<String>> rows(ResultSet r) throws SQLException {
        ResultSetMetaData meta = r.getMetaData();
        List<List<String>> out = new ArrayList<>();
        while (r.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = r.getObject(i);
                if (value == null) {
                    row.add("NULL");
                } else if (value instanceof BigDecimal d) {
                    row.add(d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString());
                } else {
                    row.add(value.toString());
                }
            }
            out.add(row);
        }
        return out;
    }

    static List<String> columns(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            ResultSetMetaData meta = r.getMetaData();
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                names.add(meta.getColumnLabel(i));
            }
            return names;
        }
    }

    /** Result of running a file with the real psql client. */
    record PsqlRun(int exitCode, String output) {
    }

    /** Runs psql -f file against this run's database, from the starter/ folder, and stops on the first error. */
    static PsqlRun psql(String file) throws IOException, InterruptedException, SQLException {
        String psql = env("PSQL", "psql");
        // -w: never prompt for a password; the password file or PGPASSWORD supplies it, as for the tests.
        ProcessBuilder pb = new ProcessBuilder(psql, "-X", "-q", "-w", "-v", "ON_ERROR_STOP=1",
                "-h", host(), "-p", env("PGPORT", "5432"), "-U", user(), "-d", owned, "-f", file);
        pb.redirectErrorStream(true);
        Path log = Files.createTempFile(RECORD.getParent(), "psql-", ".txt");
        pb.redirectOutput(log.toFile());
        Map<String, String> environment = pb.environment();
        environment.put("PGCONNECT_TIMEOUT", "10");
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            Files.deleteIfExists(log);
            throw new IOException("Could not start psql (\"" + psql + "\"). The seed task runs your script with the real psql "
                    + "client: check that psql --version works in this terminal, or set PSQL to the full path of psql "
                    + "(see README.md, Troubleshooting).", e);
        }
        process.getOutputStream().close();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = Files.readString(log, StandardCharsets.UTF_8).strip();
        Files.deleteIfExists(log);
        if (!finished) {
            return new PsqlRun(-1, "psql did not finish within 60 seconds. " + output);
        }
        return new PsqlRun(process.exitValue(), output);
    }
}
