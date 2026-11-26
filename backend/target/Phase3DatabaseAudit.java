import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

class Phase3DatabaseAudit {
    static final List<String> TABLES = List.of("sys_user","cake_merchant","cake_category","cake_product","cake_order","cake_order_item");
    public static void main(String[] args) {
        try { audit(); } catch (Exception error) {
            System.err.println("Database audit failed; credentials and vendor details suppressed.");
            System.exit(1);
        }
    }
    static void audit() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user",System.getenv("DB_USERNAME"));
        properties.setProperty("password",System.getenv("DB_PASSWORD"));
        properties.setProperty("connectionTimeZone","+08:00");
        properties.setProperty("forceConnectionTimeZoneToSession","true");
        properties.setProperty("allowPublicKeyRetrieval","false");
        properties.setProperty("sslMode","PREFERRED");
        try(Connection connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/",properties)) {
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT VERSION(),@@session.time_zone")) {
                r.next(); System.out.println("MySQL="+r.getString(1)+" timezone="+r.getString(2));
            }
            for(String schema:List.of("cake_order_system","cake_order_system_phase2_test")) {
                connection.setCatalog(schema);
                var ddl=MessageDigest.getInstance("SHA-256");
                var data=MessageDigest.getInstance("SHA-256");
                long total=0;
                System.out.println("schema="+schema);
                try(var s=connection.prepareStatement("SELECT TABLE_NAME,AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME")) {
                    s.setString(1,schema);
                    try(var r=s.executeQuery()) { while(r.next()) System.out.println("sequence "+r.getString(1)+"="+r.getString(2)); }
                }
                for(String table:TABLES) {
                    try(var s=connection.createStatement();var r=s.executeQuery("SHOW CREATE TABLE `"+table+"`")) {
                        r.next(); ddl.update(r.getString(2).replaceAll("AUTO_INCREMENT=\\d+", "AUTO_INCREMENT=<sequence>").getBytes(StandardCharsets.UTF_8));
                    }
                    long count=0;
                    try(var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM `"+table+"` ORDER BY id")) {
                        int n=r.getMetaData().getColumnCount();
                        while(r.next()) { count++; for(int c=1;c<=n;c++) data.update((Objects.toString(r.getObject(c),"<NULL>")+"\u0000").getBytes(StandardCharsets.UTF_8)); }
                    }
                    total+=count; System.out.println(table+" rows="+count);
                }
                System.out.println("structureSHA256="+HexFormat.of().formatHex(ddl.digest()));
                System.out.println("dataSHA256="+HexFormat.of().formatHex(data.digest())+" totalRows="+total);
            }
        }
    }
}
