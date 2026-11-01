package com.minipro;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Handles the Approve button on the list page.
 *
 * HOW IT'S CALLED: AJAX, NOT A NORMAL PAGE LOAD
 * Clicking Approve doesn't load a new page. js/requisitions-list.js sends a request in
 * the background with jQuery's $.ajax, reads this servlet's reply, and updates just
 * that one table row. So instead of HTML, this servlet answers with a small piece of
 * JSON (JavaScript Object Notation), which is data the JavaScript can read:
 *
 *   Request:   POST /mini-procurement/requisitions/approve    body: id=11
 *
 *   Replies:   200 OK           {"reqId":11,"status":"APPROVED","poId":7}
 *              400 Bad Request  {"error":"Missing or invalid requisition ID."}
 *              409 Conflict     {"error":"Requisition 11 can't be approved: ..."}
 *              500              Tomcat's HTML error page (something unexpected broke)
 *
 * WHY POST AND NOT GET
 * Approving changes data. GET requests should never change anything: browsers,
 * link previews and search engines may send GETs on their own, and a GET can be
 * repeated just by pressing Refresh. Anything that changes data uses POST.
 *
 * WHY A SEPARATE SERVLET
 * One URL, one job. RequisitionServlet handles the list and the form; this one only
 * approves. It's mapped to "/requisitions/approve", so its full URL is
 * /mini-procurement/requisitions/approve.
 */
@WebServlet("/requisitions/approve")
public class ApproveRequisitionServlet extends HttpServlet {

    // Shared by every request thread. Safe because the DAO keeps no per-request data.
    private final RequisitionDao dao = new RequisitionDao();

    // Only doPost is written. A GET to this URL gets "405 Method Not Allowed"
    // automatically from HttpServlet.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // ---- 1. Read and check the ID -------------------------------------------------
        // jQuery sent { id: 11 } as the form-style parameter "id=11".
        // Integer.parseInt(null) also throws NumberFormatException, so this one catch
        // covers both a missing id and a non-numeric one like "abc".
        int reqId;
        try {
            reqId = Integer.parseInt(request.getParameter("id"));
        } catch (NumberFormatException e) {
            // In a Java string, \" is a quote character inside the string, so this is
            // the JSON text {"error":"Missing or invalid requisition ID."}
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"error\":\"Missing or invalid requisition ID.\"}");
            return;   // stop here; the response has been sent
        }

        // ---- 2. Approve it (one database transaction, see RequisitionDao.approve) ------
        Integer poId;   // Integer, not int, because approve() returns null for "not approved"
        try {
            poId = dao.approve(reqId);
        } catch (SQLException e) {
            // Unexpected database failure: 500, with the stack trace in
            // logs\localhost.<date>.log. The JavaScript shows a general error message.
            throw new ServletException("Could not approve requisition " + reqId, e);
        }

        // ---- 3. Reply -------------------------------------------------------------------
        if (poId == null) {
            // 409 Conflict: the request itself was fine, but the requisition's current
            // state doesn't allow it. It's already approved or rejected, or the ID
            // doesn't exist. This also covers someone else approving it a moment earlier.
            sendJson(response, HttpServletResponse.SC_CONFLICT,
                    "{\"error\":\"Requisition " + reqId + " can't be approved: it doesn't exist or isn't pending.\"}");
            return;
        }

        // 200 OK. The JavaScript reads result.reqId, result.status and result.poId.
        sendJson(response, HttpServletResponse.SC_OK,
                "{\"reqId\":" + reqId + ",\"status\":\"APPROVED\",\"poId\":" + poId + "}");
    }

    // Writes a JSON reply with the given HTTP status code.
    //
    // The JSON is built by gluing strings together, which is only safe because every
    // value here is a number or text we wrote ourselves. Text that came from a user
    // could contain a " character and break the JSON, so a real app would use a JSON
    // library (such as Jackson) that escapes values correctly.
    private static void sendJson(HttpServletResponse response, int status, String json) throws IOException {
        // setStatus (not sendError): sendError would make Tomcat write its own HTML
        // error page; setStatus just sets the code and lets us write the body ourselves.
        response.setStatus(status);
        // Tells the browser the body is JSON. jQuery sees this and parses it into a
        // JavaScript object automatically.
        response.setContentType("application/json");
        // Must be set BEFORE getWriter(), which picks its encoding when it's created.
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}
