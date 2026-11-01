-- =====================================================================================
-- seed.sql: adds the vendors and some sample requisitions
--
-- HOW TO RUN IT
-- Run it after schema.sql, on the "procurement" database:
--   psql -d procurement -f db/seed.sql
--
-- It's safe to run more than once: vendors that exist are skipped, and the sample
-- requisitions are only added when the requisitions table is empty.
-- =====================================================================================

-- The six vendors the form's dropdown offers (requisition.html has IDs 1 to 6).
-- ON CONFLICT DO NOTHING skips a row whose vendor_id is already there.
INSERT INTO vendors (vendor_id, name) VALUES
    (1, 'Palmetto Office Supply'),
    (2, 'Aiken Industrial Tools'),
    (3, 'Augusta Lab Equipment'),
    (4, 'Carolina Safety Gear'),
    (5, 'Southeast Computer Systems'),
    (6, 'Riverbend Janitorial')
ON CONFLICT (vendor_id) DO NOTHING;

-- Sample requisitions, so the list isn't empty on a fresh database. The dates count
-- back from today so they always look recent. WHERE NOT EXISTS adds them only when the
-- table is empty, so running this again doesn't add duplicates.
INSERT INTO requisitions (requester, item, quantity, unit_cost, vendor_id, status, created_on)
SELECT requester, item, quantity, unit_cost, vendor_id, status, CURRENT_DATE - days_ago
FROM (VALUES
    ('Marcus Lee',     'Cordless drill kit',             2, 189.00, 2, 'APPROVED', 9),
    ('Tom Alvarez',    'Safety glasses, box of 12',      5,  38.75, 4, 'APPROVED', 7),
    ('Dana Whitfield', 'Printer paper, 10-ream case',    4,  42.99, 1, 'PENDING',  5),
    ('Priya Raman',    'Digital pipette set',            1, 645.50, 3, 'PENDING',  3),
    ('Jen Carter',     '27-inch monitor',                3, 229.99, 5, 'PENDING',  2),
    ('Sam Brooks',     'Floor cleaner, 5-gallon',        2,  64.00, 6, 'PENDING',  1)
) AS sample (requester, item, quantity, unit_cost, vendor_id, status, days_ago)
WHERE NOT EXISTS (SELECT 1 FROM requisitions);

-- Every APPROVED requisition needs its purchase order, the same row approve() would
-- have created. NOT EXISTS skips requisitions that already have one. (ON CONFLICT would
-- also work, but it still takes a number from the po_id sequence for each skipped row,
-- leaving gaps in the PO numbers.)
INSERT INTO purchase_orders (req_id, total)
SELECT r.req_id, r.quantity * r.unit_cost
FROM requisitions r
WHERE r.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM purchase_orders p WHERE p.req_id = r.req_id);
