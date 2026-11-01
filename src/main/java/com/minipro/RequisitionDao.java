package com.minipro;

// JDBC (Java Database Connectivity) types, all from the standard java.sql package:
import java.sql.Connection;          // an open connection (session) to the database
import java.sql.PreparedStatement;   // a SQL statement with ? placeholders for values
import java.sql.ResultSet;           // the rows a SELECT returns, read one at a time
import java.sql.SQLException;        // what JDBC throws when anything goes wrong
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO = "Data Access Object": the one class that runs SQL against the requisitions
 * and purchase_orders tables.
 *
 * WHY A SEPARATE CLASS
 * The servlets deal with HTTP (read the request, choose a response). This class deals
 * with the database. Keeping them apart means:
 *   - each class has one job, so it's easier to read and change
 *   - the SQL is all in one place, so it's easy to find and review
 *   - the database code could be tested, or reused, without a web server
 *
 * THE PATTERN EVERY METHOD FOLLOWS
 *   1. Open a connection:      Db.getConnection()
 *   2. Prepare the SQL:        conn.prepareStatement(SQL)
 *   3. Fill in the ? values:   ps.setString(1, ...), ps.setInt(2, ...)
 *   4. Run it:                 executeQuery() for SELECT, executeUpdate() for INSERT/UPDATE/DELETE
 *   5. Read the results:       while (rs.next()) { ... }
 *   6. Close everything:       done automatically by try-with-resources
 *
 * Every public method says "throws SQLException". That's a CHECKED exception, so the
 * compiler makes each caller deal with it. The servlets decide what a failure means
 * for the user (a 400 or a 500 response).
 *
 * THREAD SAFETY
 * The servlets keep one RequisitionDao and use it from many threads at once. That's
 * safe because this class has no fields that change: only constant SQL strings.
 * Every method call gets its own connection, statement and results.
 */
public class RequisitionDao {

    // ---------------------------------------------------------------------------------
    // The SQL statements. "private static final" = a constant: one copy shared by the
    // whole class, which can never be reassigned.
    //
    // Each statement is split into several strings joined with +. Watch the spaces at
    // the end of each piece: without the space after "requisitions r", the pieces
    // would join into "requisitions rJOIN ...", which is a syntax error.
    // ---------------------------------------------------------------------------------

    // Used by the list page.
    //   SELECT ...           the columns we need. "r." and "v." are table aliases,
    //                        nicknames given to the tables in the FROM and JOIN lines.
    //   v.name AS vendor_name
    //                        renames the column in the results, so the Java code reads
    //                        it as "vendor_name" (both tables could have a "name").
    //   JOIN vendors v ON ...
    //                        pairs each requisition with its vendor row, so we can show
    //                        the vendor's NAME instead of just its ID. This is an INNER
    //                        JOIN: a requisition with no matching vendor would be left
    //                        out, but the foreign key guarantees there's always a match.
    //   ORDER BY r.req_id DESC
    //                        newest first (IDs only go up), so a just-submitted
    //                        requisition appears at the top of the list.
    private static final String FIND_ALL_SQL =
            "SELECT r.req_id, r.requester, r.item, r.quantity, r.unit_cost, "
          + "       r.vendor_id, v.name AS vendor_name, r.status, r.created_on "
          + "FROM requisitions r "
          + "JOIN vendors v ON v.vendor_id = r.vendor_id "
          + "ORDER BY r.req_id DESC";

    // Used when the form is submitted.
    //
    // The ? marks are PLACEHOLDERS, numbered 1 to 5 from left to right. The real values
    // are sent to the database separately from the SQL text, so nothing a user types
    // can change what the SQL does. That prevents SQL INJECTION.
    //
    // Example of the danger: if the SQL were built by gluing strings together,
    //   "... VALUES ('" + item + "', ...)"
    // then a user could type an item like   x'); DROP TABLE requisitions; --
    // and the database would run their DROP TABLE command. With ? placeholders, that
    // text is simply stored as an (odd) item name.
    //
    // status and created_on aren't listed, so the table's DEFAULT values fill them in:
    // 'PENDING' and today's date.
    private static final String INSERT_SQL =
            "INSERT INTO requisitions (requester, item, quantity, unit_cost, vendor_id) "
          + "VALUES (?, ?, ?, ?, ?)";

    // Used by the Approve button: step 1 of the approval transaction.
    //
    // "AND status = 'PENDING'" means only a pending requisition can be approved; an
    // approved or rejected one matches no rows, so nothing changes.
    //
    // It also handles two people clicking Approve at the same moment. The database
    // locks the row while the first UPDATE runs; the second UPDATE waits, then sees the
    // status is already APPROVED, matches 0 rows, and changes nothing. So a requisition
    // can never be approved twice or get two purchase orders.
    private static final String APPROVE_SQL =
            "UPDATE requisitions SET status = 'APPROVED' "
          + "WHERE req_id = ? AND status = 'PENDING'";

    // Used by the Approve button: step 2, creating the purchase order (PO).
    //
    //   INSERT INTO ... SELECT ...
    //       inserts the rows a SELECT produces, instead of listing VALUES. Here the
    //       SELECT reads the requisition and calculates quantity * unit_cost, so the
    //       PO total is computed by the database from the saved data.
    //   RETURNING po_id
    //       hands back the ID the database just generated for the new PO, so we don't
    //       need a second query to find it. RETURNING is PostgreSQL syntax; with other
    //       databases, JDBC's getGeneratedKeys() does the same job.
    private static final String CREATE_PO_SQL =
            "INSERT INTO purchase_orders (req_id, total) "
          + "SELECT req_id, quantity * unit_cost FROM requisitions WHERE req_id = ? "
          + "RETURNING po_id";

    /**
     * Returns every requisition, newest first. Used by RequisitionServlet.doGet().
     *
     * List<Requisition> is a "generic" type: a List that can only hold Requisition
     * objects. ArrayList is the most common List implementation (a resizable array).
     */
    public List<Requisition> findAll() throws SQLException {
        List<Requisition> requisitions = new ArrayList<>();   // <> = "same type as the left side"

        // try-with-resources: everything opened inside try ( ... ) is closed
        // automatically when the block ends, in REVERSE order (ResultSet, then
        // PreparedStatement, then Connection), even if an exception is thrown.
        // Without it, you'd need a finally block with three close() calls.
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {   // executeQuery = run a SELECT

            // A ResultSet is like a cursor that starts BEFORE the first row.
            // rs.next() moves to the next row and returns true, or returns false when
            // there are no rows left, which ends the loop.
            while (rs.next()) {
                // Copy each column of the current row into a new Requisition.
                // Columns are read by their name (or the "AS" alias) in the SELECT.
                Requisition r = new Requisition();
                r.setReqId(rs.getInt("req_id"));
                r.setRequester(rs.getString("requester"));
                r.setItem(rs.getString("item"));
                r.setQuantity(rs.getInt("quantity"));
                r.setUnitCost(rs.getBigDecimal("unit_cost"));   // NUMERIC -> exact BigDecimal
                r.setVendorId(rs.getInt("vendor_id"));
                r.setVendorName(rs.getString("vendor_name"));   // the alias from the SELECT
                r.setStatus(rs.getString("status"));
                // getObject(column, LocalDate.class) converts a SQL DATE straight into
                // Java's modern date type
                r.setCreatedOn(rs.getObject("created_on", LocalDate.class));
                requisitions.add(r);
            }
        }   // rs, ps and conn are all closed here
        return requisitions;
    }

    /**
     * Saves a new requisition. Used by RequisitionServlet.doPost() after it has
     * validated the form. The status and date come from the table's defaults.
     */
    public void insert(Requisition r) throws SQLException {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            // Fill in the ? placeholders. JDBC numbers them starting at 1, NOT 0
            // (a common mistake). Each setX method matches the Java type to the column.
            ps.setString(1, r.getRequester());       // 1st ? -> requester
            ps.setString(2, r.getItem());            // 2nd ? -> item
            ps.setInt(3, r.getQuantity());           // 3rd ? -> quantity
            ps.setBigDecimal(4, r.getUnitCost());    // 4th ? -> unit_cost
            ps.setInt(5, r.getVendorId());           // 5th ? -> vendor_id

            // executeUpdate = run an INSERT, UPDATE or DELETE. It returns how many rows
            // changed (always 1 here, so we don't need it). If vendor_id doesn't exist,
            // the database's foreign key rejects the row and this throws SQLException.
            ps.executeUpdate();
        }
    }

    /**
     * Approves a pending requisition and creates its purchase order, as ONE transaction.
     * Used by ApproveRequisitionServlet.
     *
     * Returns Integer (the object version of int) rather than int, because an Integer
     * can be null. null means "nothing was approved".
     *
     * @return the new PO's ID, or null if the requisition doesn't exist or isn't pending
     */
    public Integer approve(int reqId) throws SQLException {
        try (Connection conn = Db.getConnection()) {
            // TRANSACTIONS
            // Normally JDBC is in "auto-commit" mode: every statement is saved
            // permanently the moment it runs. Turning auto-commit off starts a
            // transaction: nothing is permanent until commit(), and rollback() undoes
            // everything since the transaction started.
            //
            // Approving takes two statements (UPDATE the requisition, INSERT the PO).
            // In one transaction they succeed or fail TOGETHER, so the data can never
            // end up half-done: an APPROVED requisition with no PO, or a PO for
            // something that was never approved. This all-or-nothing property is called
            // "atomicity" (the A in ACID).
            conn.setAutoCommit(false);

            // This inner try/catch exists so that any SQL failure rolls back first,
            // then the exception continues up to the servlet.
            try {
                // Step 1: mark it APPROVED (only if it's still PENDING)
                int updated;
                try (PreparedStatement ps = conn.prepareStatement(APPROVE_SQL)) {
                    ps.setInt(1, reqId);
                    updated = ps.executeUpdate();   // how many rows changed: 1 or 0
                }
                if (updated == 0) {
                    // 0 rows: the ID doesn't exist, or it's already approved or
                    // rejected. Nothing changed, but end the transaction cleanly.
                    conn.rollback();
                    return null;
                }

                // Step 2: create the purchase order and get its new ID
                int poId;
                // executeQuery, not executeUpdate, because RETURNING sends back a row
                // of results (the new po_id), just like a SELECT would.
                try (PreparedStatement ps = conn.prepareStatement(CREATE_PO_SQL)) {
                    ps.setInt(1, reqId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();                    // move to the one returned row
                        poId = rs.getInt("po_id");
                    }
                }

                // Both steps worked: make them permanent, together.
                conn.commit();
                return poId;   // Java "autoboxes" the int into an Integer here
            } catch (SQLException e) {
                // Something failed partway (for example the INSERT): undo the UPDATE
                // too, then rethrow so the servlet can report the error.
                conn.rollback();
                throw e;
            }
        }   // the connection is closed here, whichever path we took
    }
}
