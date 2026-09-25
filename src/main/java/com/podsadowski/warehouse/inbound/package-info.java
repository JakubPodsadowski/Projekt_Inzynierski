/**
 * Inbound receipt / putaway pipeline: receive → putaway queue → stock on mono-SKU bins
 * sized by {@code Product.unitsPerBin} with spillover across locations.
 */
package com.podsadowski.warehouse.inbound;
