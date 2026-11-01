-- =====================================================================================
-- schema.sql: creates the app's three tables
--
-- HOW TO RUN IT
-- Run it on the "procurement" database as the owner (for example postgres), BEFORE
-- create-app-user.sql, because that script grants permissions on these tables:
--   psql -d procurement -f db/schema.sql
-- Or open it in pgAdmin's Query Tool and press F5.
--
-- It's safe to run more than once: IF NOT EXISTS skips tables that are already there.
-- It never drops or changes an existing table.
--
-- HOW THE TABLES CONNECT
--   vendors 1 ──< many requisitions 1 ──< at most one purchase_orders
-- =====================================================================================

-- The companies we buy from. The IDs are fixed because the form's vendor dropdown in
-- requisition.html has them hard-coded (seed.sql adds vendors 1 to 6).
CREATE TABLE IF NOT EXISTS vendors (
    vendor_id  INTEGER       PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL
);

-- A request to buy something. The lengths and CHECKs match the validation in
-- RequisitionServlet, so the database rejects bad data even if the servlet missed it.
-- SERIAL creates the sequence requisitions_req_id_seq, which create-app-user.sql grants.
CREATE TABLE IF NOT EXISTS requisitions (
    req_id      SERIAL         PRIMARY KEY,
    requester   VARCHAR(100)   NOT NULL,
    item        VARCHAR(200)   NOT NULL,
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    unit_cost   NUMERIC(10,2)  NOT NULL CHECK (unit_cost >= 0),
    vendor_id   INTEGER        NOT NULL REFERENCES vendors (vendor_id),
    status      VARCHAR(10)    NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_on  DATE           NOT NULL DEFAULT CURRENT_DATE
);

-- Created by RequisitionDao.approve() when a requisition is approved.
-- UNIQUE (req_id) is a second safety net: even if the code had a bug, the database
-- would refuse a second purchase order for the same requisition.
CREATE TABLE IF NOT EXISTS purchase_orders (
    po_id   SERIAL   PRIMARY KEY,
    req_id  INTEGER  NOT NULL UNIQUE REFERENCES requisitions (req_id),
    total   NUMERIC  NOT NULL
);
