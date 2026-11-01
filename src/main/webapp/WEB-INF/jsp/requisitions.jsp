<%--
  REQUISITIONS LIST PAGE: the "View" in MVC
  Seen at: http://localhost:8081/mini-procurement/requisitions

  WHAT A JSP IS
  A JSP (JavaServer Page) is an HTML page with special tags and ${...} expressions mixed
  in. The first time it's requested, Tomcat's JSP engine (called Jasper) translates it
  into a Java servlet class and compiles it. You can see the generated .java file in
  Tomcat's work folder. So a JSP is really a servlet whose job is writing HTML.

  HOW THIS PAGE IS REACHED
    1. The browser sends GET /mini-procurement/requisitions
    2. RequisitionServlet.doGet() loads the rows through RequisitionDao.findAll()
    3. It stores them in a request attribute named "requisitions" (a List<Requisition>)
    4. It FORWARDS to this JSP, which turns that list into an HTML table
  This file is inside WEB-INF, so the browser can't request it directly. That makes
  sure the servlet has always loaded the data first.

  COMMENTS
  This is a JSP comment. Tomcat strips JSP comments out before sending the page, so the
  browser never sees them. HTML comments (the kind requisition.html uses) are sent to
  the browser, where anyone can read them with View Source.
--%>

<%--
  DIRECTIVES: instructions to the JSP engine, not output.
  The "page" directive says what kind of response this is. charset=UTF-8 means any
  character, like accents or symbols, comes out correctly.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%--
  TAGLIBS: tag libraries from JSTL (the JSP Standard Tag Library). Each line connects a
  short prefix to a library, so <c:forEach> means "the forEach tag from the core library".
    c   = core: loops, if/else, safe output (c:forEach, c:if, c:choose, c:out)
    fmt = formatting: numbers, money, dates (fmt:formatNumber, fmt:setLocale)
    fn  = functions: string helpers (fn:toLowerCase, fn:length)
  "jakarta.tags.core" is the JSTL 3.0 address. Older apps use
  "http://java.sun.com/jsp/jstl/core" for the same library.
  JSTL replaces "scriptlets" (raw Java code in the page), which older JSPs are full of
  and which are hard to read and maintain.
--%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Requisitions</title>
  <%--
    EXPRESSION LANGUAGE (EL): ${...}
    Code inside ${...} is evaluated on the server and replaced with its value.
    pageContext is a built-in ("implicit") object. pageContext.request.contextPath calls
    request.getContextPath(), which returns "/mini-procurement".
    Building links from the context path keeps them correct no matter which URL
    forwarded to this page, or what the app is renamed to.
  --%>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/styles.css">
</head>
<body>
  <h1>Requisitions</h1>
  <p class="page-link"><a href="${pageContext.request.contextPath}/requisition.html">+ New Requisition</a></p>

  <%-- Starts empty. js/requisitions-list.js writes a result message here after an
       Approve click. role="status" makes screen readers announce the new message. --%>
  <p id="message" class="page-link" role="status"></p>

  <%-- Use US conventions for formatting money, so 12600 becomes $12,600.00 --%>
  <fmt:setLocale value="en_US"/>

  <%--
    c:choose / c:when / c:otherwise work like if / else if / else.

    ${requisitions}: how EL finds a name. It looks in four places, in this order, and
    uses the first match: page scope, REQUEST scope, session scope, application scope.
    The servlet put "requisitions" in request scope, so that's where it's found.
    (Knowing these four scopes is a common interview question.)

    "empty" is an EL operator: true if the value is null, or an empty list or string.
  --%>
  <c:choose>
    <c:when test="${empty requisitions}">
      <p class="page-link">No requisitions yet.</p>
    </c:when>
    <c:otherwise>
      <%--
        id="requisitions-table" lets the JavaScript find the table.
        data-approve-url is a custom "data attribute": any attribute starting with
        data- can carry information for JavaScript. Here the server builds the approve
        URL, and the JavaScript reads it with $table.data('approve-url'), so the
        JavaScript file doesn't have to hard-code the app's path.
      --%>
      <table id="requisitions-table" data-approve-url="${pageContext.request.contextPath}/requisitions/approve">
        <%-- thead holds the header row; tbody holds the data rows --%>
        <thead>
          <tr>
            <th>ID</th>
            <th>Requester</th>
            <th>Item</th>
            <th>Vendor</th>
            <th>Qty</th>
            <th>Unit Cost</th>
            <th>Line Total</th>
            <th>Status</th>
            <th>Created</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          <%--
            c:forEach is a loop: for each item in ${requisitions}, put it in a variable
            named "r" and output everything between the tags. So this writes one table
            row per requisition. It's like the Java loop:  for (Requisition r : requisitions)

            ${r.reqId} calls r.getReqId() on the Requisition object. EL turns the
            property name "reqId" into the getter name "getReqId". That's why
            Requisition must be a JavaBean with getters.
          --%>
          <c:forEach var="r" items="${requisitions}">
            <tr>
              <td>${r.reqId}</td>
              <%--
                c:out AND XSS (cross-site scripting)
                c:out "escapes" HTML special characters before writing them: < becomes
                &lt;  > becomes &gt;  and so on. The browser then DISPLAYS them as
                text instead of treating them as HTML.
                Without it, a user could type <script></script> as an item name, and
                that script would run in the browser of everyone who views this list.
                Plain ${...} in the page is NOT escaped, so use c:out for any text that
                came from users (requester, item). Numbers and dates are safe as-is.
              --%>
              <td><c:out value="${r.requester}"/></td>
              <td><c:out value="${r.item}"/></td>
              <td><c:out value="${r.vendorName}"/></td>
              <td>${r.quantity}</td>
              <%-- type="currency" adds the $ sign, thousands commas and 2 decimals --%>
              <td><fmt:formatNumber value="${r.unitCost}" type="currency"/></td>
              <%-- ${r.lineTotal} calls getLineTotal(), which is calculated, not stored --%>
              <td><fmt:formatNumber value="${r.lineTotal}" type="currency"/></td>
              <%--
                Two CSS classes on this cell:
                  "status"                     lets the JavaScript find the cell to update it
                  fn:toLowerCase(r.status)     turns PENDING into "pending", APPROVED into
                                               "approved", and so on, matching the color
                                               classes in styles.css
              --%>
              <td class="status ${fn:toLowerCase(r.status)}">${r.status}</td>
              <%-- A LocalDate prints as year-month-day, like 2026-10-25 --%>
              <td>${r.createdOn}</td>
              <td>
                <%--
                  c:if outputs its contents only when the test is true, so only
                  pending requisitions get an Approve button.
                  In EL, == compares VALUES (like Java's .equals()). In Java, == on two
                  Strings checks whether they're the same OBJECT, a classic bug.
                  type="button": a plain button that does nothing by itself (it's not
                  in a form, and it shouldn't submit anything). The JavaScript adds the
                  click behavior.
                  data-id carries this requisition's ID to the click handler, which
                  reads it with $button.data('id').
                --%>
                <c:if test="${r.status == 'PENDING'}">
                  <button type="button" class="approve-btn" data-id="${r.reqId}">Approve</button>
                </c:if>
              </td>
            </tr>
          </c:forEach>
        </tbody>
      </table>
    </c:otherwise>
  </c:choose>

  <%--
    Scripts at the end of the body. jQuery must load before our script, which uses $.
    integrity: the browser refuses jQuery if the CDN ever serves a different file (see
    requisition.html for the full explanation).
    Our own script's URL is built from the context path, like the stylesheet above.
  --%>
  <script src="https://code.jquery.com/jquery-3.7.1.min.js"
          integrity="sha256-/JqT3SQfawRcv/BIHPThkBvs0OEvtFFmqPF/lYI/Cxo="
          crossorigin="anonymous"></script>
  <script src="${pageContext.request.contextPath}/js/requisitions-list.js"></script>
</body>
</html>
