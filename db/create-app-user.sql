-- =====================================================================================
-- create-app-user.sql: creates the database user the web app logs in as
--
-- HOW TO RUN IT
-- In pgAdmin, open the Query Tool on the "procurement" database while logged in as
-- the postgres user (the owner of the tables), paste this in, and press F5.
--
-- Replace CHANGE_ME with a password you choose, in pgAdmin only. Don't save your real
-- password into this file: it's in your GitHub folder. Put the same password in
-- Tomcat's bin\setenv.bat as DB_PASSWORD, then restart Tomcat.
--
-- WHY THE APP GETS ITS OWN USER: THE PRINCIPLE OF LEAST PRIVILEGE
-- The postgres user can do anything: drop tables, read every database, create users.
-- If the app connected as postgres, any bug (or attacker) that got the app to run the
-- wrong SQL could do all of that. So the app gets a user that can do ONLY what the app
-- needs, and nothing more. For example, it has no DELETE permission, so even a bad bug
-- can't wipe the requisitions table.
--
-- To check who has what: in pgAdmin, right-click a table, choose Properties, and open
-- the Security tab.
-- =====================================================================================

-- In PostgreSQL, users and groups are both "roles". LOGIN is what makes a role a user
-- that can connect. To change the password later:
--   ALTER ROLE minipro_app PASSWORD 'new-password';
CREATE ROLE minipro_app WITH LOGIN PASSWORD 'CHANGE_ME';

-- GRANT gives a role permission to do something. Nothing is allowed until granted.

-- Permission to connect to this one database (and no others), and to use the objects
-- in its default schema, "public", where the tables live.
GRANT CONNECT ON DATABASE procurement TO minipro_app;
GRANT USAGE ON SCHEMA public TO minipro_app;

-- Vendors: read-only. The app shows vendor names but never changes vendors.
GRANT SELECT ON vendors TO minipro_app;

-- Requisitions and purchase orders: read (SELECT), add (INSERT), change (UPDATE).
-- Deliberately NOT granted: DELETE, TRUNCATE, DROP.
GRANT SELECT, INSERT, UPDATE ON requisitions, purchase_orders TO minipro_app;

-- SERIAL id columns get their next number from a SEQUENCE, a counter object the
-- database created alongside each table (named table_column_seq). An INSERT that
-- leaves the id out calls nextval() on that sequence, which needs USAGE permission.
-- Sequences never go backwards, even when an INSERT fails, which is why IDs can
-- have gaps.
GRANT USAGE ON SEQUENCE requisitions_req_id_seq, purchase_orders_po_id_seq TO minipro_app;
