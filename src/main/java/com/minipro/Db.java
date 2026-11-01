package com.minipro;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens connections to the PostgreSQL "procurement" database.
 *
 * WHERE THIS FITS
 * Every database call in the app goes through getConnection():
 *
 *   RequisitionServlet / ApproveRequisitionServlet   (handle the HTTP request)
 *     -> RequisitionDao                              (runs the SQL)
 *       -> Db.getConnection()                        (this class: opens the connection)
 *         -> PostgreSQL on localhost, port 5432
 *
 * WHERE THE SETTINGS COME FROM
 * The database URL, user name and password are NOT written in this code. They are
 * environment variables that Tomcat's bin\setenv.bat sets every time Tomcat starts:
 *
 *   setenv.bat:   set "DB_URL=jdbc:postgresql://localhost:5432/procurement"
 *   Java:         System.getenv("DB_URL")   returns that same text
 *
 * setenv.bat lives in Tomcat's folder, outside this project, so the password never
 * ends up in Git. Interview point: keep secrets (passwords, API keys) out of source code.
 *
 * REAL-WORLD NOTE
 * This opens a brand-new connection every time, which is slow: each one means a
 * network handshake plus a login. Production apps use a connection POOL instead, a
 * set of already-open connections that code borrows and gives back. In Tomcat that's
 * a "JNDI DataSource" configured in context.xml. DriverManager keeps this app simple.
 */
public final class Db {   // final: no other class can extend (inherit from) this one

    // A "static initializer" block. It runs exactly once: when Java first loads this
    // class, which happens the first time any code calls Db.getConnection().
    static {
        // JDBC drivers register themselves with DriverManager when their class is
        // loaded. Usually that happens automatically, but Tomcat sets up DriverManager
        // before any web app starts, so it never notices drivers packaged inside an
        // app's WEB-INF/lib. Class.forName("...") loads the PostgreSQL driver class by
        // its full name, and loading it makes it register itself.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            // This only happens if the driver JAR is missing from the WAR (check pom.xml).
            // The app can't work without it, so stop with a clear message.
            throw new ExceptionInInitializerError("PostgreSQL JDBC driver is missing from WEB-INF/lib");
        }
    }

    // A private constructor stops anyone from writing "new Db()". Everything in this
    // class is static (it belongs to the class itself, not to an object), so creating
    // an instance would be pointless. This is the usual pattern for a "utility class".
    private Db() {
    }

    /**
     * Opens a new connection to the database.
     *
     * The caller MUST close the connection when it's done. Otherwise connections pile
     * up until the database refuses new ones. The safe way is try-with-resources, as
     * RequisitionDao does:
     *
     *   try (Connection conn = Db.getConnection()) {
     *       ... use conn ...
     *   }   // conn.close() runs here automatically, even if an exception was thrown
     *
     * "throws SQLException" means this method can fail with a checked exception, so
     * every caller must either catch it or declare "throws SQLException" itself.
     * Examples: the database isn't running, or "password authentication failed".
     */
    public static Connection getConnection() throws SQLException {
        // DriverManager looks through its registered drivers for one that understands
        // URLs starting with "jdbc:postgresql:", then asks it to log in with this user
        // name and password. The result is an open connection to the database.
        return DriverManager.getConnection(env("DB_URL"), env("DB_USER"), env("DB_PASSWORD"));
    }

    // Reads one required setting from the environment.
    //
    // "Fail fast": if setenv.bat is missing a value, or Tomcat wasn't restarted after
    // setenv.bat was edited, stop right here with a message that says exactly what to
    // fix, instead of failing later with a confusing connection error.
    private static String env(String name) {
        String value = System.getenv(name);   // returns null if the variable isn't set
        if (value == null || value.isEmpty()) {
            // IllegalStateException is an UNCHECKED exception (a subclass of
            // RuntimeException), so callers aren't forced to catch it. It means
            // "the app is set up wrong", which code can't fix at runtime anyway.
            // Note: "\\" in a Java string is ONE backslash; a single \ starts an escape.
            throw new IllegalStateException(name + " is not set. Add it to Tomcat's bin\\setenv.bat and restart Tomcat.");
        }
        return value;
    }
}
