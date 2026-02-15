package com.pedrodalben.ecosystem.tax;

/**
 * Where tax revenue goes.
 */
public enum TaxDestination {
    /** Tax money disappears from the economy (deflationary) */
    SINK,
    /** Tax money goes to a virtual server treasury account */
    SERVER_ACCOUNT,
    /** Tax money goes to the shop owner (for chest shops) */
    SHOP_OWNER
}
