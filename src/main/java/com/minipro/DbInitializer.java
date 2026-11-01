package com.minipro;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates the tables and sample data the first time the app starts on an empty database.
 *
 * WHEN IT RUNS
 * A ServletContextListener is told when the web app starts and stops. "@WebListener"
 * registers it with Tomcat, the same way @WebServlet registers a servlet, so Tomcat
 * calls contextInitialized() once, as the app starts and before any request is handled.
 *
 * WHAT IT DOES
 * If the requisitions table doesn't exist yet, it runs db/schema.sql and db/seed.sql
 * (pom.xml copies them into the WAR). If the table is already there, it does nothing,
 * so a restart never touches existing data. Locally, where you created the tables by
 * hand, it always skips.
 *
 * Both scripts run in ONE transaction: if anything fails, nothing is left half-created,
 * and the next start tries again from the beginning. (PostgreSQL can roll back CREATE
 * TABLE; many other databases can't.)
 */
@WebListener
public class DbInitializer implements ServletContextListener {

    // java.util.logging writes to Tomcat's console, which is what Railway shows as the
    // deploy logs. (ServletContext.log() would go to a file inside the container instead.)
    private static final Logger LOG = Logger.getLogger(DbInitializer.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try (Connection conn = Db.getConnection()) {
            if (tableExists(conn, "requisitions")) {
                LOG.info("Database already set up; skipping schema.sql and seed.sql.");
                return;
            }

            conn.setAutoCommit(false);
            try {
                run(conn, "/db/schema.sql");
                run(conn, "/db/seed.sql");
                conn.commit();
                LOG.info("Empty database: created the tables and sample data.");
            } catch (SQLException | IOException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | IOException | RuntimeException e) {
            // Log it and let the app start anyway. Throwing here would stop Tomcat from
            // deploying the app at all, so every page would be a bare 404. This way the
            // pages show the real database error, and the cause is in the log.
            LOG.log(Level.SEVERE, "Could not set up the database", e);
        }
    }

    // to_regclass('requisitions') returns the table's internal ID, or NULL if there's no
    // table with that name. Unlike "SELECT ... FROM requisitions", it doesn't fail when
    // the table is missing, and it doesn't need permission to read the table.
    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT to_regclass('" + table + "') IS NOT NULL")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    // Reads a .sql file from inside the WAR and runs it. A plain Statement (not a
    // PreparedStatement) is fine here: the SQL comes from our own file, not from a user,
    // and the PostgreSQL driver runs a script of several statements in one call.
    private static void run(Connection conn, String path) throws SQLException, IOException {
        String sql;
        try (InputStream in = DbInitializer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException(path + " is missing from the WAR (check <resources> in pom.xml)");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
