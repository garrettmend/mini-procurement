# Interview Study Guide

Where each topic for the SRMC Associate Java Developer technical screen shows up in this app. Links jump to the exact line (in VS Code's Markdown preview and on GitHub).

Every file has detailed comments written for a learner. Each one starts with a header explaining what the file does and how it connects to the rest of the app.

## Suggested reading order

Follow a requisition through the app, from the form to the database and back:

1. [requisition.html](src/main/webapp/requisition.html): the form the user fills in
2. [js/requisition-form.js](src/main/webapp/js/requisition-form.js): the browser-side checks before it's sent
3. [RequisitionServlet.java](src/main/java/com/minipro/RequisitionServlet.java): receives the form (`doPost`) and shows the list (`doGet`)
4. [Requisition.java](src/main/java/com/minipro/Requisition.java): the object that carries one requisition's data
5. [RequisitionDao.java](src/main/java/com/minipro/RequisitionDao.java): all the SQL
6. [Db.java](src/main/java/com/minipro/Db.java): opens the database connection
7. [requisitions.jsp](src/main/webapp/WEB-INF/jsp/requisitions.jsp): turns the list into an HTML table
8. [js/requisitions-list.js](src/main/webapp/js/requisitions-list.js): the Approve button's AJAX call
9. [ApproveRequisitionServlet.java](src/main/java/com/minipro/ApproveRequisitionServlet.java): approves and answers with JSON
10. [styles.css](src/main/webapp/styles.css), [pom.xml](pom.xml), [db/create-app-user.sql](db/create-app-user.sql): the look, the build, and the database permissions

## The 2-minute project pitch

> "To prepare, I built a small procurement app modeled on Puridiom's procure-to-pay flow: requisition, then approval, then purchase order. Users submit requisitions through an HTML form. A Java servlet validates the input on the server and saves it to PostgreSQL through a DAO, using PreparedStatements. The list page is a JSP using JSTL. Approvals use jQuery AJAX: the server approves the requisition and creates the purchase order in a single transaction, then returns JSON. It's built with Maven, deployed as a WAR to Tomcat, and connects with a limited database user whose password is kept out of source control."

## Core Java

| Topic | Where | What to say |
|---|---|---|
| Encapsulation, JavaBeans | [Requisition.java:47](src/main/java/com/minipro/Requisition.java#L47) | Private fields with getters and setters. JSP reads data through the getters. |
| Computed getter | [Requisition.java:68](src/main/java/com/minipro/Requisition.java#L68) | `getLineTotal()` isn't stored anywhere, but `${r.lineTotal}` still works because EL calls the getter |
| `BigDecimal` for money | [RequisitionServlet.java:168](src/main/java/com/minipro/RequisitionServlet.java#L168) | `double` has rounding errors. `scale()` counts the decimal places ([:177](src/main/java/com/minipro/RequisitionServlet.java#L177)). |
| Exception handling | [RequisitionServlet.java:153](src/main/java/com/minipro/RequisitionServlet.java#L153), [:213](src/main/java/com/minipro/RequisitionServlet.java#L213) | Catch `NumberFormatException` (unchecked). Wrap `SQLException` (checked) in a `ServletException`. |
| try-with-resources | [RequisitionDao.java:133](src/main/java/com/minipro/RequisitionDao.java#L133) | Closes the ResultSet, statement, and connection automatically, in reverse order |
| Static initializer, private constructor | [Db.java:45](src/main/java/com/minipro/Db.java#L45), [:56](src/main/java/com/minipro/Db.java#L56) | The driver loads once when the class loads. A utility class shouldn't be instantiated. |
| `Integer` vs. `int` | [RequisitionDao.java:190](src/main/java/com/minipro/RequisitionDao.java#L190) | `approve()` returns `Integer` so it can return `null` for "not approved" |
| Thread safety | [RequisitionServlet.java:67](src/main/java/com/minipro/RequisitionServlet.java#L67), [:121](src/main/java/com/minipro/RequisitionServlet.java#L121) | One servlet instance serves many threads. The shared DAO holds no state, and request data stays in local variables. |

## Servlets

| Topic | Where | What to say |
|---|---|---|
| URL mapping | [RequisitionServlet.java:49](src/main/java/com/minipro/RequisitionServlet.java#L49) | `@WebServlet`. Older apps do the same in `web.xml`, so be able to read both. |
| `doGet` / `doPost` | [:80](src/main/java/com/minipro/RequisitionServlet.java#L80), [:110](src/main/java/com/minipro/RequisitionServlet.java#L110) | GET reads data and POST changes it. A method you don't implement returns 405. |
| `getParameter` | [:121](src/main/java/com/minipro/RequisitionServlet.java#L121) | Always returns a String, or `null`. Matches each input's `name` attribute. |
| Request scope | [:97](src/main/java/com/minipro/RequisitionServlet.java#L97) | `setAttribute` passes data to the JSP for this one request |
| **forward** | [:103](src/main/java/com/minipro/RequisitionServlet.java#L103) | Happens inside the server. The browser's URL doesn't change. |
| **sendRedirect** (Post/Redirect/Get) | [:231](src/main/java/com/minipro/RequisitionServlet.java#L231) | A new request from the browser, so refreshing doesn't resubmit the form |
| HTTP status codes | 400 at [:246](src/main/java/com/minipro/RequisitionServlet.java#L246), 409 at [ApproveRequisitionServlet.java:78](src/main/java/com/minipro/ApproveRequisitionServlet.java#L78), 500 at [:213](src/main/java/com/minipro/RequisitionServlet.java#L213) | 400 means bad input, 409 means the record's state doesn't allow the action, 500 means a server failure |
| JSON endpoint | [ApproveRequisitionServlet.java:37](src/main/java/com/minipro/ApproveRequisitionServlet.java#L37) | Returns data for JavaScript to use instead of an HTML page |

## JSP / JSTL

| Topic | Where | What to say |
|---|---|---|
| JSP inside `WEB-INF` | [requisitions.jsp:1](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L1) | Private: reachable only through the servlet, which loads the data first |
| Taglibs | [:43](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L43) | Core (`c`), formatting (`fmt`), and functions (`fn`) |
| Loops and conditions | `c:forEach` [:120](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L120), `c:choose` [:82](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L82), `c:if` [:163](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L163) | Tags instead of Java code (scriptlets) in the page |
| **`c:out` prevents XSS** | [:134](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L134) | Escapes HTML. Plain `${...}` doesn't. |
| Formatting | [:140](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L140) | `fmt:formatNumber` turns 12600 into `$12,600.00` |
| Context path | [:59](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L59) | Links still work wherever the app is deployed |

## SQL / JDBC

| Topic | Where | What to say |
|---|---|---|
| **PreparedStatement, SQL injection** | [RequisitionDao.java:86](src/main/java/com/minipro/RequisitionDao.java#L86) | `?` placeholders send the values separately, so user input can't change the query |
| JOIN | [:69](src/main/java/com/minipro/RequisitionDao.java#L69) | Shows vendor names instead of IDs |
| Reading a ResultSet | [:138](src/main/java/com/minipro/RequisitionDao.java#L138) | Loop with `while (rs.next())` and map each row to an object |
| **Transactions** | [:203](src/main/java/com/minipro/RequisitionDao.java#L203), [:234](src/main/java/com/minipro/RequisitionDao.java#L234), [:239](src/main/java/com/minipro/RequisitionDao.java#L239) | `setAutoCommit(false)`, then `commit` or `rollback`: the approval and the PO save together or not at all |
| Concurrency guard | [:99](src/main/java/com/minipro/RequisitionDao.java#L99) | `WHERE status = 'PENDING'`: two simultaneous clicks can only approve once |
| INSERT…SELECT…RETURNING | [:113](src/main/java/com/minipro/RequisitionDao.java#L113) | Creates the PO and gets its new ID in a single query |
| `executeQuery` vs. `executeUpdate` | [:223](src/main/java/com/minipro/RequisitionDao.java#L223) | `executeQuery` returns rows. `executeUpdate` returns a count of changed rows. |
| Database error codes | [RequisitionServlet.java:56](src/main/java/com/minipro/RequisitionServlet.java#L56) | Error 23503 (bad foreign key) means the user's input was wrong, so return 400, not 500 |
| **Least privilege** | [create-app-user.sql:36](db/create-app-user.sql#L36) | The app's user can't DELETE or DROP |

## JavaScript / jQuery

| Topic | Where | What to say |
|---|---|---|
| `$(function(){})` | [requisition-form.js:28](src/main/webapp/js/requisition-form.js#L28) | The same as `$(document).ready`: runs after the page's HTML has loaded |
| Stopping a form submit | [:84](src/main/webapp/js/requisition-form.js#L84) | `preventDefault()` |
| Regular expressions | [:69](src/main/webapp/js/requisition-form.js#L69) | Checks the format of input |
| `.text()` vs. `.html()` | [:126](src/main/webapp/js/requisition-form.js#L126) | `.text()` can't inject HTML |
| **Event delegation** | [requisitions-list.js:36](src/main/webapp/js/requisitions-list.js#L36) | One handler on the table covers every button in it |
| `data-` attributes | [requisitions-list.js:38](src/main/webapp/js/requisitions-list.js#L38), [requisitions.jsp:94](src/main/webapp/WEB-INF/jsp/requisitions.jsp#L94) | Pass values from the server-built page to the JavaScript |
| **AJAX** | [requisitions-list.js:68](src/main/webapp/js/requisitions-list.js#L68), [`.done`](src/main/webapp/js/requisitions-list.js#L74), [`.fail`](src/main/webapp/js/requisitions-list.js#L85) | Updates part of the page without a reload |
| Subresource integrity | [requisition.html:124](src/main/webapp/requisition.html#L124) | The browser rejects the CDN file if it's been altered |

## HTML / CSS

| Topic | Where | What to say |
|---|---|---|
| `name` connects to `getParameter` | [requisition.html:96](src/main/webapp/requisition.html#L96) | The form sends each field under its `name` |
| `label for` / `id` | [:72](src/main/webapp/requisition.html#L72) | Accessibility: screen readers, and clicking the label focuses the field |
| Option value vs. text | [:100](src/main/webapp/requisition.html#L100) | The form sends the value. The user sees the text. |
| Box model | [styles.css:37](src/main/webapp/styles.css#L37) | `border-box` counts padding and border inside the width |
| Pseudo-classes | [:98](src/main/webapp/styles.css#L98) | `:focus`, `:hover` |
| Specificity | [:185](src/main/webapp/styles.css#L185) | `td button` beats `button`, which is how the table buttons get their own style |

## Maven and Tomcat

| Topic | Where | What to say |
|---|---|---|
| WAR packaging | [pom.xml:40](pom.xml#L40), [:116](pom.xml#L116) | `mini-procurement.war` deploys at `/mini-procurement` |
| **`provided` scope** | [pom.xml:69](pom.xml#L69), [:108](pom.xml#L108) | Tomcat supplies these, so they stay out of the WAR. The EL API case shows a dependency pulled in indirectly causing a conflict. |
| App dependencies | [pom.xml:77](pom.xml#L77) | The driver ships in `WEB-INF/lib` |
| Configuration through environment variables | [Db.java:87](src/main/java/com/minipro/Db.java#L87) plus Tomcat's `bin\setenv.bat` | Secrets stay out of Git |
| Ports, logs, deploying | Outside the repo, in the Tomcat install folder | You changed the port to 8081 in `conf\server.xml`. Exceptions go to `logs\localhost.<date>.log`. Deploy by copying the WAR into `webapps`. |

## Not covered by the app (study separately)

- **SQL:** `GROUP BY`, `HAVING`, `LEFT JOIN`, and `COUNT(*)` vs. `COUNT(column)`. Your 8 practice queries cover these.
- **Servlet lifecycle** (`init` and `destroy`), **sessions and cookies** (Tomcat's Sessions example at `http://localhost:8081/examples/servlets/`), and reading **`web.xml`**.
- **`javax` vs. `jakarta`**, and **connection pooling / JNDI DataSources**, which is how real enterprise apps get connections instead of `DriverManager`.
- **Unit tests (JUnit).** The app has none, so if asked, say how you'd test the DAO and the validation code.
- **The database schema isn't in this repo.** The tables and sample data exist only in pgAdmin, so someone cloning it from GitHub couldn't run it.
- **The Reject button:** building it yourself is the best way to practice the whole pattern. It's the approve flow without the PO: `UPDATE ... SET status = 'REJECTED' WHERE req_id = ? AND status = 'PENDING'`, a servlet, and a click handler.
