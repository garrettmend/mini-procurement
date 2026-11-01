# Mini Procurement

A small Java web app that follows a procure-to-pay flow: someone submits a **requisition**, someone **approves** it, and approval creates a **purchase order**.

It's built with plain servlets, JSP/JSTL, JDBC and jQuery, with no framework, so every step from the HTML form to the SQL is visible. Every source file has detailed comments for learners. [STUDY-GUIDE.md](STUDY-GUIDE.md) shows where each concept appears in the code.

## Features

- **New requisition form** with validation in the browser (jQuery) and again on the server
- **Requisitions list** rendered by a JSP with JSTL, showing vendor names (SQL `JOIN`), line totals and color-coded status
- **One-click approval over AJAX**: the server approves the requisition and creates its purchase order in a single database transaction, then replies with JSON
- **Safe by default**: `PreparedStatement` everywhere (no SQL injection), `c:out` for user text (no XSS), Post/Redirect/Get after form submits, and a limited database user with no `DELETE` or `DROP`

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Web | Jakarta Servlet 6.0, JSP, JSTL 3.0 |
| Server | Apache Tomcat 10.1 |
| Database | PostgreSQL, through the JDBC driver (no ORM) |
| Front end | HTML, CSS, jQuery 3.7 |
| Build | Maven, packaged as a WAR |

## How it works

```
requisition.html ──POST /requisitions──▶ RequisitionServlet.doPost ──▶ RequisitionDao.insert
                                              │ 302 redirect
                                              ▼
requisitions.jsp ◀──forward── RequisitionServlet.doGet ◀── RequisitionDao.findAll

Approve button ──AJAX POST /requisitions/approve──▶ ApproveRequisitionServlet
                                                       └─▶ RequisitionDao.approve
                                                             (UPDATE + INSERT PO, one transaction)
                ◀── JSON {"reqId":11,"status":"APPROVED","poId":7}
```

### Endpoints

All paths are under the context path `/mini-procurement`.

| Method | Path | What it does |
|---|---|---|
| GET | `/requisition.html` | The new requisition form |
| GET | `/requisitions` | Lists all requisitions, newest first |
| POST | `/requisitions` | Creates a requisition from form fields `requester`, `item`, `quantity`, `unit_cost`, `vendor_id`. Redirects to the list, or returns `400` for invalid input. |
| POST | `/requisitions/approve` | Approves the requisition with form field `id`. Returns JSON: `200` with the new PO ID, `400` for a missing or invalid ID, `409` if it doesn't exist or isn't pending. |

## Getting started

### Prerequisites

- JDK 21
- Maven 3
- Apache Tomcat 10.1 (it must be 10.x: the code uses the `jakarta.*` packages, not `javax.*`)
- PostgreSQL, plus a client such as pgAdmin or `psql`

### 1. Create the database

Create a database named `procurement` with these tables. The columns listed are the ones the app reads and writes.

| Table | Columns |
|---|---|
| `vendors` | `vendor_id` (primary key), `name VARCHAR(100)` |
| `requisitions` | `req_id SERIAL` (primary key), `requester VARCHAR(100)`, `item VARCHAR(200)`, `quantity INT CHECK (quantity > 0)`, `unit_cost NUMERIC(10,2)`, `vendor_id` (foreign key to `vendors`), `status VARCHAR(10) DEFAULT 'PENDING'`, `created_on DATE DEFAULT CURRENT_DATE` |
| `purchase_orders` | `po_id SERIAL` (primary key), `req_id` (foreign key to `requisitions`), `total NUMERIC` |

> **Note:** the `CREATE TABLE` script isn't in this repo yet.

The vendor dropdown in [requisition.html](src/main/webapp/requisition.html) is hard-coded with vendor IDs 1 to 6, so add vendors with those IDs:

| `vendor_id` | `name` |
|---|---|
| 1 | Palmetto Office Supply |
| 2 | Aiken Industrial Tools |
| 3 | Augusta Lab Equipment |
| 4 | Carolina Safety Gear |
| 5 | Southeast Computer Systems |
| 6 | Riverbend Janitorial |

### 2. Create the app's database user

Run [db/create-app-user.sql](db/create-app-user.sql) on the `procurement` database as the tables' owner (for example `postgres`). Replace `CHANGE_ME` with a real password when you run it, but don't save the password into the file.

This creates `minipro_app`, which can read vendors and read, insert and update requisitions and purchase orders, and nothing else.

### 3. Give Tomcat the connection settings

The app reads its database settings from environment variables, so no password is ever stored in the code. Create `setenv.bat` (Windows) or `setenv.sh` (macOS/Linux) in Tomcat's `bin` folder:

```bat
rem bin\setenv.bat
set "DB_URL=jdbc:postgresql://localhost:5432/procurement"
set "DB_USER=minipro_app"
set "DB_PASSWORD=your-password"
```

```sh
# bin/setenv.sh
export DB_URL="jdbc:postgresql://localhost:5432/procurement"
export DB_USER="minipro_app"
export DB_PASSWORD="your-password"
```

Restart Tomcat after creating or changing this file. If a variable is missing, the app stops with an error that names it.

### 4. Build and deploy

```sh
mvn clean package
```

This produces `target/mini-procurement.war`. Copy it into Tomcat's `webapps` folder, then open:

- Form: `http://localhost:8080/mini-procurement/requisition.html`
- List: `http://localhost:8080/mini-procurement/requisitions`

Use your Tomcat port if it isn't 8080 (it's set in `conf/server.xml`). If something fails with a 500 error, the stack trace is in Tomcat's `logs/localhost.<date>.log`.

## Project structure

```
mini-procurement/
├── pom.xml                              Maven build: dependencies and WAR packaging
├── db/
│   └── create-app-user.sql              Least-privilege database user
├── src/main/java/com/minipro/
│   ├── RequisitionServlet.java          GET: list page. POST: create a requisition.
│   ├── ApproveRequisitionServlet.java   POST: approve, replies with JSON
│   ├── RequisitionDao.java              All the SQL, including the approval transaction
│   ├── Requisition.java                 The model (a JavaBean)
│   └── Db.java                          Opens JDBC connections from environment variables
├── src/main/webapp/
│   ├── requisition.html                 New requisition form
│   ├── styles.css
│   ├── js/
│   │   ├── requisition-form.js          Client-side form validation
│   │   └── requisitions-list.js         Approve button (AJAX)
│   └── WEB-INF/jsp/
│       └── requisitions.jsp             List page; private, only reachable through the servlet
└── STUDY-GUIDE.md                       Where each Java/web concept appears in the code
```

## Known limitations

- **No database schema script.** The tables have to be created by hand (see [step 1](#1-create-the-database)).
- **Vendors are hard-coded** in the form instead of loaded from the `vendors` table.
- **No Reject action.** Requisitions can only be approved.
- **No connection pooling.** `Db` opens a new connection per request with `DriverManager`. A production app would use a pooled JNDI `DataSource`.
- **No automated tests.**
