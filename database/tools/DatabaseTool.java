import java.io.Console;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Phase 2 initialization utility, not part of the application JAR. No credentials are saved. */
class DatabaseTool {
    private static final Set<String> TABLES = Set.of("sys_user", "cake_merchant", "cake_category",
            "cake_product", "cake_order", "cake_order_item");
    private static final Set<String> COLLATIONS = Set.of("utf8mb4_0900_as_cs", "utf8mb4_0900_ai_ci");

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("check", "init", "seed").contains(args[0])) {
            System.err.println("Usage: DatabaseTool.java check|init|seed (run from project root)");
            System.exit(2);
        }
        try {
            execute(args[0]);
        } catch (Exception failure) {
            // Do not print exceptions, JDBC URLs, SQL, bound values or database error messages.
            String code = failure instanceof SQLException sql ? "; SQLState=" + sql.getSQLState()
                    + "; vendorCode=" + sql.getErrorCode() : "";
            System.err.println("Operation failed. Check connection, empty-schema guard, supplied parameters"
                    + " and initialization guide" + code + ". No credentials logged.");
            System.exit(1);
        }
    }

    private static void execute(String mode) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isRegularFile(root.resolve("database/schema/01_create_tables.sql")))
            throw new IllegalArgumentException("Run from project root");
        String host = value("DB_HOST", "127.0.0.1");
        if (!Set.of("127.0.0.1", "localhost", "::1").contains(host))
            throw new IllegalArgumentException("This utility only supports the local development server");
        String port = value("DB_PORT", "3306");
        int number = Integer.parseInt(port);
        if (number < 1 || number > 65535) throw new IllegalArgumentException("Port out of range");
        String schema = value("DB_NAME", "cake_order_system");
        if (!schema.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("Schema name");
        String username = required("DB_USERNAME", false);
        char[] password = secret("DB_PASSWORD");
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", new String(password));
        Arrays.fill(password, '\0');
        properties.setProperty("connectionTimeZone", "+08:00");
        properties.setProperty("forceConnectionTimeZoneToSession", "true");
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        properties.setProperty("allowPublicKeyRetrieval", "false");
        properties.setProperty("sslMode", "PREFERRED");
        Class.forName("com.mysql.cj.jdbc.Driver");
        String address = host.equals("::1") ? "[::1]" : host;
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + address + ":" + port + "/", properties)) {
            properties.remove("password");
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT VERSION(),@@session.time_zone")) {
                rows.next();
                String serverVersion = rows.getString(1);
                if (!serverVersion.startsWith("8.0.") || !rows.getString(2).equals("+08:00"))
                    throw new IllegalStateException("Expected MySQL 8.0.x and +08:00");
                System.out.println("Connected: MySQL " + serverVersion + "; session +08:00.");
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION'");
            }
            if (mode.equals("check")) {
                System.out.println("Connection probe complete. No schema or data changes.");
                return;
            }
            String collation = required("CAKE_DB_COLLATION", false);
            if (!COLLATIONS.contains(collation)) throw new IllegalArgumentException("Unsupported collation");
            if (mode.equals("init")) {
                // No IF NOT EXISTS on tables: an existing object never gets treated as matching this design.
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=?")) {
                    query.setString(1, schema);
                    try (ResultSet rows = query.executeQuery()) {
                        rows.next();
                        if (rows.getLong(1) != 0) throw new IllegalStateException("Schema must be empty");
                    }
                }
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?")) {
                    query.setString(1, schema);
                    try (ResultSet rows = query.executeQuery()) {
                        if (rows.next() && (!rows.getString(1).equals("utf8mb4") || !rows.getString(2).equals(collation)))
                            throw new IllegalStateException("Existing schema settings differ");
                    }
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE IF NOT EXISTS `" + schema + "` CHARACTER SET utf8mb4 COLLATE " + collation);
                }
                connection.setCatalog(schema);
                for (String file : List.of("schema/01_create_tables.sql", "indexes/02_create_indexes.sql",
                        "constraints/03_data_constraints.sql")) {
                    for (String sql : statements(root.resolve("database").resolve(file))) {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute(sql.replace("__CAKE_DB_COLLATION__", collation));
                        }
                    }
                    System.out.println("Executed: " + file);
                }
                verifyTables(connection, schema);
                System.out.println("Initialization completed: 6 tables, 74 columns; " + collation + ".");
                System.out.println("Run the opt-in mysql-test suite to verify constraints. No account seeds executed.");
            } else {
                connection.setCatalog(schema);
                verifyTables(connection, schema);
                Map<String, Object> parameters = seedParameters();
                connection.setAutoCommit(false);
                try {
                    for (String table : TABLES) {
                        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(
                                "SELECT COUNT(*) FROM `" + table + "`")) {
                            rows.next();
                            if (rows.getLong(1) != 0) throw new IllegalStateException("Seed only an empty database");
                        }
                    }
                    for (String sql : statements(root.resolve("database/seed/04_initial_data.sql"))) {
                        Matcher matcher = Pattern.compile(":([a-z_]+)").matcher(sql);
                        List<String> names = new ArrayList<>();
                        StringBuffer prepared = new StringBuffer();
                        while (matcher.find()) { names.add(matcher.group(1)); matcher.appendReplacement(prepared, "?"); }
                        matcher.appendTail(prepared);
                        try (PreparedStatement statement = connection.prepareStatement(prepared.toString())) {
                            for (int i = 0; i < names.size(); i++) {
                                if (!parameters.containsKey(names.get(i))) throw new IllegalArgumentException("Unknown seed parameter");
                                statement.setObject(i + 1, parameters.get(names.get(i)));
                            }
                            if (statement.executeUpdate() != 1) throw new IllegalStateException("Expected one seed row");
                        }
                    }
                    connection.commit();
                    System.out.println("Seed transaction committed: 2 accounts, 1 merchant, 1 category, 1 product.");
                } catch (Exception failure) { connection.rollback(); throw failure; }
                finally { parameters.clear(); }
            }
        } finally { properties.clear(); }
    }

    private static void verifyTables(Connection connection, String schema) throws SQLException {
        Set<String> actual = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=?")) {
            query.setString(1, schema);
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) actual.add(rows.getString(1)); }
        }
        if (!actual.equals(TABLES)) throw new IllegalStateException("Unexpected schema objects");
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=?")) {
            query.setString(1, schema);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                if (rows.getInt(1) != 74) throw new IllegalStateException("Expected 74 columns");
            }
        }
    }

    private static Map<String, Object> seedParameters() {
        Map<String, Object> result = new HashMap<>();
        for (String name : List.of("admin_phone", "admin_hash", "admin_nickname", "merchant_phone", "merchant_hash",
                "merchant_nickname", "shop_name", "shop_phone", "shop_address", "category_name", "product_name", "product_spec")) {
            String value = required("CAKE_SEED_" + name.toUpperCase(Locale.ROOT), name.endsWith("_hash"));
            if (name.endsWith("_phone") && !value.matches("1[3-9][0-9]{9}")) throw new IllegalArgumentException("Phone format");
            if (name.endsWith("_hash") && !value.matches("\\$2[aby]\\$(1[0-9]|2[0-9]|3[01])\\$[./A-Za-z0-9]{53}"))
                throw new IllegalArgumentException("BCrypt cost must be at least 10");
            int max = name.endsWith("_nickname") || name.equals("category_name") || name.equals("product_spec") ? 30
                    : name.equals("shop_address") ? 120 : name.endsWith("_hash") ? 100 : 60;
            if (value.codePointCount(0, value.length()) > max) throw new IllegalArgumentException("Seed value too long");
            result.put(name, value);
        }
        if (result.get("admin_phone").equals(result.get("merchant_phone"))) throw new IllegalArgumentException("Account phones must differ");
        String amount = required("CAKE_SEED_PRODUCT_PRICE", false);
        if (!amount.matches("[0-9]{1,8}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Price precision");
        BigDecimal price = new BigDecimal(amount);
        if (price.signum() <= 0) throw new IllegalArgumentException("Positive price required");
        result.put("product_price", price);
        int stock = Integer.parseInt(required("CAKE_SEED_PRODUCT_STOCK", false));
        if (stock < 0) throw new IllegalArgumentException("Nonnegative stock required");
        result.put("product_stock", stock);
        return result;
    }

    private static List<String> statements(Path path) throws Exception {
        // These controlled scripts contain no routines, quoted semicolons or multiline SQL comments.
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (!line.stripLeading().startsWith("--")) sql.append(line).append('\n');
        }
        return Arrays.stream(sql.toString().split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static String required(String name, boolean sensitive) {
        if (sensitive) { char[] value = secret(name); String result = new String(value); Arrays.fill(value, '\0'); return result; }
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            Console console = System.console();
            if (console == null) throw new IllegalStateException("Missing " + name + "; use a local interactive console");
            value = console.readLine("%s: ", name);
        }
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required input");
        return value.trim();
    }
    private static char[] secret(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) return value.toCharArray();
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Missing local secret");
        char[] secret = console.readPassword("%s (hidden): ", name);
        if (secret == null || secret.length == 0) throw new IllegalArgumentException("Missing local secret");
        return secret;
    }
}
