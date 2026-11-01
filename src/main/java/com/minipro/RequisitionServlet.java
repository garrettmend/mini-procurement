package com.minipro;

// Servlet API classes. They come from the jakarta.servlet-api dependency in pom.xml
// ("provided" scope: Tomcat supplies them at runtime). Tomcat 9 and older used
// "javax.servlet" instead of "jakarta.servlet"; the code is otherwise the same.
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * The "Controller" in MVC for the URL /mini-procurement/requisitions.
 *
 * WHAT HAPPENS FOR EACH KIND OF REQUEST
 *
 *   GET /mini-procurement/requisitions   (clicking a link, or typing the URL)
 *     -> doGet()
 *        -> RequisitionDao.findAll()            load all requisitions (the Model)
 *        -> forward to requisitions.jsp         render them as an HTML table (the View)
 *
 *   POST /mini-procurement/requisitions  (submitting the form in requisition.html)
 *     -> doPost()
 *        -> check every form field              reply 400 Bad Request if anything is wrong
 *        -> RequisitionDao.insert()             save the new row
 *        -> redirect to GET /requisitions       the browser then shows the updated list
 *
 * HOW TOMCAT FINDS THIS CLASS
 * When the app is deployed, Tomcat scans the compiled classes for @WebServlet
 * annotations. "/requisitions" is relative to the app's context path,
 * "/mini-procurement", which comes from the WAR file's name (mini-procurement.war).
 * Older apps register servlets in WEB-INF/web.xml instead; same result.
 *
 * SERVLET LIFECYCLE AND THREADS
 *   - Tomcat creates ONE object of this class and calls init() on it once.
 *   - For EVERY request, Tomcat calls service() on that same object, each request on
 *     its own thread. HttpServlet.service() looks at the HTTP method and calls doGet(),
 *     doPost(), and so on.
 *   - A method we don't override (like doPut or doDelete) answers
 *     "405 Method Not Allowed" automatically.
 *   - destroy() runs once when the app is stopped.
 * Because many threads share the one object, fields must never hold data about a
 * particular request. Request data always goes in local variables (see doPost).
 */
@WebServlet("/requisitions")
public class RequisitionServlet extends HttpServlet {   // extends = inherits from HttpServlet

    // PostgreSQL's error code (its "SQLState") for a foreign key violation: the row
    // refers to something that doesn't exist, such as a vendor_id with no vendor.
    // SQLStates are standard five-character codes; ones starting with "23" mean a
    // data rule (constraint) was broken. 23505, for example, is a duplicate unique value.
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    // Where the list page's JSP lives. Anything under WEB-INF is private: the browser
    // can't request it directly (Tomcat answers 404). The only way to reach this page
    // is through doGet below, which loads the data first.
    private static final String LIST_JSP = "/WEB-INF/jsp/requisitions.jsp";

    // One servlet object serves every request, on many threads at once, so this field
    // is shared by all of them. That's safe ONLY because the DAO stores nothing about
    // any particular request (it has no changing fields). "final" means this field
    // always points to the same DAO object.
    private final RequisitionDao dao = new RequisitionDao();

    /**
     * Shows the list of requisitions.
     *
     * "@Override" asks the compiler to confirm this really replaces a method from
     * HttpServlet. If the name were misspelled (doget), compiling would fail instead of
     * the method silently never being called.
     *
     * request  = everything the browser sent (URL, form fields, headers, cookies)
     * response = what we send back (status code, headers, page body)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // 1. Load the data (the Model) through the DAO
        List<Requisition> requisitions;
        try {
            requisitions = dao.findAll();
        } catch (SQLException e) {
            // We can't show the page without data. Wrapping the SQLException in a
            // ServletException hands it to Tomcat, which shows a "500 Internal Server
            // Error" page and writes the full stack trace to
            // logs\localhost.<date>.log, which is where to look when debugging.
            throw new ServletException("Could not load requisitions", e);
        }

        // 2. Hand the data to the JSP. A request ATTRIBUTE is a named object attached
        //    to this one request ("request scope"); it disappears when the response is
        //    sent. In the JSP, ${requisitions} finds it by this name.
        request.setAttribute("requisitions", requisitions);

        // 3. FORWARD to the JSP (the View), which writes the HTML.
        //    A forward happens entirely inside the server: the same request and response
        //    objects are passed to the JSP, and the browser never knows. Its address bar
        //    still shows /requisitions, never the JSP's path.
        request.getRequestDispatcher(LIST_JSP).forward(request, response);
    }

    /**
     * Handles the New Requisition form: check it, save it, then redirect.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // ---- 1. Read the form fields ------------------------------------------------
        // getParameter("x") returns the value of the form field whose name="x" (see
        // requisition.html). It ALWAYS returns a String, even for numbers, or null if
        // that field wasn't sent at all. clean() (bottom of this class) turns null into
        // "" and trims surrounding spaces.
        //
        // These are LOCAL variables, so each request (each thread) gets its own copies.
        // If they were fields, two users submitting at once could overwrite each
        // other's values.
        String requester = clean(request.getParameter("requester"));
        String item = clean(request.getParameter("item"));
        String quantityText = clean(request.getParameter("quantity"));
        String unitCostText = clean(request.getParameter("unit_cost"));
        String vendorIdText = clean(request.getParameter("vendor_id"));

        // ---- 2. Validate on the server ----------------------------------------------
        // The browser already checks these (js/requisition-form.js), but browser checks
        // are a convenience, not protection: anyone can skip them by turning off
        // JavaScript or sending the request with a tool like curl. The server must
        // check everything again, and its rules match the database's.
        //
        // IMPORTANT: badRequest() sends an error response, but it doesn't stop this
        // method. The "return;" after each one is what stops it. Without it, the code
        // would carry on and try to save the bad data.

        // Text fields: required, and no longer than the column allows (VARCHAR(100)
        // and VARCHAR(200)). "||" means OR.
        if (requester.isEmpty() || requester.length() > 100) {
            badRequest(response, "Requester is required (100 characters max).");
            return;
        }
        if (item.isEmpty() || item.length() > 200) {
            badRequest(response, "Item is required (200 characters max).");
            return;
        }

        // Quantity: must be a whole number. Integer.parseInt("abc") or parseInt("")
        // throws NumberFormatException (an unchecked exception), which we catch and
        // turn into a friendly 400 response.
        int quantity;
        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException e) {
            badRequest(response, "Quantity must be a whole number.");
            return;
        }
        if (quantity < 1) {   // matches the table's CHECK (quantity > 0)
            badRequest(response, "Quantity must be at least 1.");
            return;
        }

        // Unit cost: parsed as BigDecimal, not double, because money must be exact
        // (with double, 0.1 + 0.2 isn't exactly 0.3). new BigDecimal("abc") also throws
        // NumberFormatException.
        BigDecimal unitCost;
        try {
            unitCost = new BigDecimal(unitCostText);
        } catch (NumberFormatException e) {
            badRequest(response, "Unit cost must be a number.");
            return;
        }
        // signum() is -1 for negative, 0 for zero, 1 for positive.
        // scale() is how many digits come after the decimal point ("12.50" has scale 2).
        // The column is NUMERIC(10,2), which would silently round 1.234 to 1.23, so we
        // reject more than 2 decimals instead of changing what the user typed.
        if (unitCost.signum() < 0 || unitCost.scale() > 2) {
            badRequest(response, "Unit cost must be zero or more, with at most 2 decimal places.");
            return;
        }

        // Vendor: must be a number. The select's empty first option sends "", which
        // fails here. We don't check whether the vendor EXISTS; the database's foreign
        // key does that (see the catch block below).
        int vendorId;
        try {
            vendorId = Integer.parseInt(vendorIdText);
        } catch (NumberFormatException e) {
            badRequest(response, "Please choose a vendor.");
            return;
        }

        // ---- 3. Save through the DAO ------------------------------------------------
        // Put the checked values into a Requisition object (the Model) and let the
        // DAO write it to the database. This servlet never writes SQL itself.
        Requisition r = new Requisition();
        r.setRequester(requester);
        r.setItem(item);
        r.setQuantity(quantity);
        r.setUnitCost(unitCost);
        r.setVendorId(vendorId);
        try {
            dao.insert(r);
        } catch (SQLException e) {
            // e.getSQLState() tells us WHY the database refused. A foreign key
            // violation means the vendor_id doesn't exist, which is the user's mistake,
            // so it's a 400 Bad Request, not a server failure.
            if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                badRequest(response, "That vendor doesn't exist.");
                return;
            }
            // Anything else is unexpected (database down, bug): 500, logged by Tomcat
            throw new ServletException("Could not save requisition", e);
        }

        // ---- 4. Redirect: the Post/Redirect/Get pattern -------------------------------
        // Instead of showing a page in response to the POST, tell the browser to go
        // and GET the list page. The browser receives "302 Found" with a Location
        // header, then makes a brand-new GET request to that URL.
        //
        // Why: if we showed the list directly in reply to the POST, pressing Refresh
        // would re-send the POST and save a duplicate requisition. After a redirect,
        // Refresh just repeats the harmless GET.
        //
        // Redirect vs forward (a classic interview question):
        //   forward  = inside the server, one request, the URL doesn't change (doGet)
        //   redirect = the browser makes a second request, the URL changes (here)
        //
        // getContextPath() returns "/mini-procurement", so this builds
        // "/mini-procurement/requisitions" without hard-coding the app's name.
        response.sendRedirect(request.getContextPath() + "/requisitions");
    }

    // Turns a missing field (null) into "" and removes spaces at both ends, so the
    // checks above never have to worry about null.
    // "condition ? a : b" is the ternary operator: a if the condition is true, else b.
    // "static" because it doesn't use any fields of the servlet object.
    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    // Sends "400 Bad Request" with a message. sendError() lets Tomcat build the
    // response body: its standard error page, showing our message. (The approve servlet
    // uses setStatus() instead, so it can write a JSON body itself.)
    private static void badRequest(HttpServletResponse response, String message) throws IOException {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, message);   // SC_BAD_REQUEST = 400
    }
}
