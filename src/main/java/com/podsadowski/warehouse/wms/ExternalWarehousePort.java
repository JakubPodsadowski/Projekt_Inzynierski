package com.podsadowski.warehouse.wms;

import java.util.List;

/**
 * Port toward an external warehouse management / order system.
 * Production would call a real HTTP/SOAP client; demo uses {@link MockExternalWarehouseAdapter}.
 */
public interface ExternalWarehousePort {

    /** Push customer demand into the local intake queue (from external system or API inject). */
    void submitDemand(ExternalDemandMessage message);

    /** Push inbound shipment into the local intake queue. */
    void submitInbound(ExternalInboundMessage message);

    /** Pull pending demand messages (FIFO). */
    List<ExternalDemandMessage> pollDemand(int max);

    /** Pull pending inbound shipments (FIFO). */
    List<ExternalInboundMessage> pollInbound(int max);

    int pendingDemandCount();

    int pendingInboundCount();
}
