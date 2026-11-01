package com.minipro;

import java.math.BigDecimal;   // exact decimal numbers, used for money
import java.time.LocalDate;    // a date with no time of day, like 2026-10-25

/**
 * One requisition: a request to buy something. This is the "Model" in MVC
 * (Model = the data, View = the JSP page, Controller = the servlet).
 *
 * WHERE IT'S USED
 *   - RequisitionDao.findAll() turns each database row into one of these objects.
 *   - RequisitionServlet.doPost() fills one in from the submitted form, then saves it.
 *   - requisitions.jsp loops over a list of them to build the HTML table.
 *
 * HOW THE FIELDS MATCH THE DATABASE
 *
 *   Java field   Java type    Database column       SQL type
 *   ----------   ----------   -------------------   ---------------------------------
 *   reqId        int          requisitions.req_id   SERIAL (ID the database generates)
 *   requester    String       requester             VARCHAR(100)
 *   item         String       item                  VARCHAR(200)
 *   quantity     int          quantity              INT
 *   unitCost     BigDecimal   unit_cost             NUMERIC(10,2)
 *   vendorId     int          vendor_id             INT (foreign key to vendors)
 *   vendorName   String       vendors.name          VARCHAR(100), from a JOIN
 *   status       String       status                VARCHAR(10): PENDING/APPROVED/REJECTED
 *   createdOn    LocalDate    created_on            DATE
 *
 * WHY IT'S WRITTEN AS A "JAVABEAN"
 * A JavaBean is a Java convention with three rules:
 *   1. Fields are private, so other classes can't change them directly (encapsulation).
 *   2. Each field has a public getter (getItem) and setter (setItem).
 *   3. There's a public constructor with no arguments. We didn't write one, so Java
 *      adds one automatically; that's what "new Requisition()" calls.
 * The JSP depends on rule 2: writing ${r.item} in the JSP calls r.getItem().
 *
 * Why not a Java record (record Requisition(int reqId, ...))? A record's accessor
 * methods are named item(), not getItem(), and the Expression Language version in
 * Tomcat 10.1 only knows how to call getters. (Tomcat 11 understands records.)
 */
public class Requisition {

    // The fields. "private" means only code inside this class can use them directly;
    // everyone else goes through the getters and setters below.
    private int reqId;
    private String requester;
    private String item;
    private int quantity;
    private BigDecimal unitCost;   // BigDecimal, never double, for money (see below)
    private int vendorId;
    private String vendorName;     // not in the requisitions table; comes from the JOIN with vendors
    private String status;
    private LocalDate createdOn;

    /**
     * The line total: quantity times unit cost. In the JSP, ${r.lineTotal} calls this.
     *
     * It's calculated, not stored in a field, so it can never disagree with the
     * quantity and unit cost it comes from.
     *
     * Why BigDecimal: double stores numbers in binary, so many decimals can't be stored
     * exactly (0.1 + 0.2 gives 0.30000000000000004). BigDecimal stores exact decimals.
     * BigDecimal objects are immutable (they never change): multiply() doesn't change
     * unitCost, it returns a NEW BigDecimal with the result.
     * BigDecimal.valueOf(quantity) turns the int into a BigDecimal so the two can be
     * multiplied; you can't use the * operator on BigDecimal.
     */
    public BigDecimal getLineTotal() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    // ---------------------------------------------------------------------------------
    // Getters and setters. They all follow the same pattern:
    //   getX()        returns the field's value
    //   setX(value)   stores a new value in the field
    // Inside a setter, "this.reqId" means the FIELD and plain "reqId" means the
    // PARAMETER. They have the same name, so "this." tells them apart.
    // ---------------------------------------------------------------------------------

    public int getReqId() {
        return reqId;
    }

    public void setReqId(int reqId) {
        this.reqId = reqId;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public int getVendorId() {
        return vendorId;
    }

    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDate createdOn) {
        this.createdOn = createdOn;
    }
}
