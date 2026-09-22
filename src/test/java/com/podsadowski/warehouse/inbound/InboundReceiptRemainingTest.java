package com.podsadowski.warehouse.inbound;

import com.podsadowski.warehouse.domain.entity.InboundReceipt;
import com.podsadowski.warehouse.domain.entity.InboundReceiptLine;
import com.podsadowski.warehouse.domain.entity.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboundReceiptRemainingTest {

    @Test
    void remainingToPutAway_sumsOpenLineQuantities() {
        Product product = new Product();
        product.setId(1L);

        InboundReceiptLine line1 = new InboundReceiptLine();
        line1.setProduct(product);
        line1.setQuantityExpected(10);
        line1.setQuantityPutAway(4);

        InboundReceiptLine line2 = new InboundReceiptLine();
        line2.setProduct(product);
        line2.setQuantityExpected(5);
        line2.setQuantityPutAway(5);

        InboundReceipt receipt = new InboundReceipt();
        receipt.addLine(line1);
        receipt.addLine(line2);

        assertThat(receipt.remainingToPutAway()).isEqualTo(6);
        assertThat(receipt.totalExpected()).isEqualTo(15);
    }
}
