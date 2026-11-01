# Mini Procurement

A small Java web app that follows a procure-to-pay flow: someone submits a **requisition**, someone **approves** it, and approval creates a **purchase order**.

It's built with plain servlets, JSP/JSTL, JDBC and jQuery, with no framework, so every step from the HTML form to the SQL is visible. Every source file has detailed comments for learners.

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

Create a database named `procurement`, then run the two scripts in [db/](db/) on it as the owner (for example `postgres`):

```sh
psql -U postgres -c "CREATE DATABASE procurement"
psql -U postgres -d procurement -f db/schema.sql -f db/seed.sql
```

- [schema.sql](db/schema.sql) creates the `vendors`, `requisitions` and `purchase_orders` tables.
- [seed.sql](db/seed.sql) adds vendors 1 to 6 (the IDs the form's dropdown is hard-coded with) and six sample requisitions.

Both scripts are safe to run again: they skip tables and rows that already exist.

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

## Deploying to Railway

The [Dockerfile](Dockerfile) builds the WAR with Maven, then runs it in the official `tomcat:10.1-jdk21` image as `ROOT.war`, so the app is served at `/` instead of `/mini-procurement`. Tomcat listens on Railway's `PORT` variable (8080 if it isn't set).

1. **Create the services.** In a new Railway project, choose **Deploy from GitHub repo** and pick this repo. Railway finds the Dockerfile and builds it. Then add **+ New → Database → PostgreSQL**.

2. **Set the app's variables.** On the app service's **Variables** tab (not the database's):

   ```
   DATABASE_URL=${{Postgres.DATABASE_URL}}
   PORT=8080
   ```

   `${{Postgres.DATABASE_URL}}` is a Railway reference: it copies the database's connection URL, which points at its private address, so the app reaches it over Railway's internal network. `Postgres` is the database service's name; change it if yours is named differently.

3. **Make it public.** Under the app service's **Settings → Networking**, click **Generate Domain** and use port 8080. The site's root URL redirects to the requisitions list.

On its first start, the app sees the database is empty and runs [schema.sql](db/schema.sql) and [seed.sql](db/seed.sql) itself ([DbInitializer.java](src/main/java/com/minipro/DbInitializer.java)). The deploy log says `Empty database: created the tables and sample data.` Later starts skip this and leave the data alone.

`DATABASE_URL` logs in as the database owner. To use the least-privilege `minipro_app` user instead, run [create-app-user.sql](db/create-app-user.sql) on the Railway database (connect with its `DATABASE_PUBLIC_URL`, then `\password minipro_app`), and set `DB_URL`, `DB_USER` and `DB_PASSWORD` on the app. Those take priority over `DATABASE_URL`.

To check the image on your machine first: `docker build -t mini-procurement .`, then `docker run -p 8080:8080 -e DATABASE_URL=postgresql://user:password@host:5432/dbname mini-procurement`. From inside the container, the host's PostgreSQL is `host.docker.internal`, not `localhost`.

## Project structure

```
mini-procurement/
├── pom.xml                              Maven build: dependencies and WAR packaging
├── Dockerfile                           Builds the WAR and runs it in Tomcat (used by Railway)
├── db/
│   ├── schema.sql                       Creates the tables
│   ├── seed.sql                         Vendors and sample requisitions
│   └── create-app-user.sql              Least-privilege database user
├── src/main/java/com/minipro/
│   ├── RequisitionServlet.java          GET: list page. POST: create a requisition.
│   ├── ApproveRequisitionServlet.java   POST: approve, replies with JSON
│   ├── RequisitionDao.java              All the SQL, including the approval transaction
│   ├── Requisition.java                 The model (a JavaBean)
│   ├── Db.java                          Opens JDBC connections from environment variables
│   └── DbInitializer.java               Creates the tables and sample data on an empty database
└── src/main/webapp/
    ├── index.jsp                        Home page: redirects to the requisitions list
    ├── requisition.html                 New requisition form
    ├── styles.css
    ├── js/
    │   ├── requisition-form.js          Client-side form validation
    │   └── requisitions-list.js         Approve button (AJAX)
    └── WEB-INF/jsp/
        └── requisitions.jsp             List page; private, only reachable through the servlet
```

## Known limitations

- **Vendors are hard-coded** in the form instead of loaded from the `vendors` table.
- **No Reject action.** Requisitions can only be approved.
- **No connection pooling.** `Db` opens a new connection per request with `DriverManager`. A production app would use a pooled JNDI `DataSource`.
- **No automated tests.**
