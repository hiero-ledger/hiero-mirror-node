// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.state;

import static com.hedera.services.fees.pricing.ResourceProvider.*;
import static com.hedera.services.fees.pricing.UsableResource.*;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageAccumulatorTest {
    private final int memoBytes = 100;
    private final int numTransfers = 2;
    private final SigUsage sigUsage = new SigUsage(2, 101, 1);

    private final long baseBpr = INT_SIZE;
    private final long baseVpt = sigUsage.numSigs();
    private final long baseBpt = BASIC_TX_BODY_SIZE + memoBytes + sigUsage.sigsSize();
    private final long baseRbs =
            RECEIPT_STORAGE_TIME_SEC * (BASIC_TX_RECORD_SIZE + memoBytes + BASIC_ACCOUNT_AMT_SIZE * numTransfers);
    private final long baseNetworkRbs = RECEIPT_STORAGE_TIME_SEC * BASIC_RECEIPT_SIZE;

    private final UsageAccumulator subject = new UsageAccumulator();

    @BeforeEach
    void setUp() {
        // No op
    }

    @Test
    void understandsNetworkPartitioning() {
        // when:
        subject.addBpt(1);
        subject.addVpt(4);
        subject.addNetworkRbs(8 * HRS_DIVISOR);

        // then:
        assertEquals(1, subject.getUniversalBpt());
        assertEquals(1, subject.get(NETWORK, BPT));
        assertEquals(4, subject.getNetworkVpt());
        assertEquals(4, subject.get(NETWORK, VPT));
        assertEquals(8, subject.getNetworkRbh());
        assertEquals(8, subject.get(NETWORK, RBH));
        assertEquals(1, subject.get(NETWORK, CONSTANT));
    }

    @Test
    void understandsNodePartitioning() {
        // when:
        subject.resetForTransaction(new BaseTransactionMeta(memoBytes, numTransfers), sigUsage);
        subject.addBpt(1);
        subject.addBpr(2);
        subject.addSbpr(3);

        // then:
        assertEquals(baseBpt + 1, subject.getUniversalBpt());
        assertEquals(baseBpt + 1, subject.get(NODE, BPT));
        assertEquals(baseBpr + 2, subject.getNodeBpr());
        assertEquals(baseBpr + 2, subject.get(NODE, BPR));
        assertEquals(3, subject.getNodeSbpr());
        assertEquals(3, subject.get(NODE, SBPR));
        assertEquals(sigUsage.numPayerKeys(), subject.getNodeVpt());
        assertEquals(sigUsage.numPayerKeys(), subject.get(NODE, VPT));
        assertEquals(1, subject.get(NODE, CONSTANT));
    }

    @Test
    void understandsServicePartitioning() {
        // when:
        subject.addRbs(6 * HRS_DIVISOR);
        subject.addSbs(7 * HRS_DIVISOR);

        // then:
        assertEquals(6, subject.getServiceRbh());
        assertEquals(6, subject.get(SERVICE, RBH));
        assertEquals(7, subject.getServiceSbh());
        assertEquals(7, subject.get(SERVICE, SBH));
        assertEquals(1, subject.get(SERVICE, CONSTANT));
    }

    @Test
    void resetWorksForTxn() {
        // given:
        subject.addSbpr(3);
        subject.addGas(5);
        subject.addSbs(7);

        // when:
        subject.resetForTransaction(new BaseTransactionMeta(memoBytes, numTransfers), sigUsage);

        // then:
        assertEquals(baseBpr, subject.getBpr());
        assertEquals(baseVpt, subject.getVpt());
        assertEquals(baseBpt, subject.getBpt());
        assertEquals(baseRbs, subject.getRbs());
        assertEquals(baseNetworkRbs, subject.getNetworkRbs());
        assertEquals(sigUsage.numPayerKeys(), subject.getNumPayerKeys());
        // and:
        assertEquals(0, subject.getSbpr());
        assertEquals(0, subject.getGas());
        assertEquals(0, subject.getSbs());
    }

    @Test
    void addersWork() {
        // given:
        subject.addBpt(1);
        subject.addBpr(2);
        subject.addSbpr(3);
        subject.addVpt(4);
        subject.addGas(5);
        subject.addRbs(6);
        subject.addSbs(7);
        subject.addNetworkRbs(8);

        // expect:
        assertEquals(1, subject.getBpt());
        assertEquals(2, subject.getBpr());
        assertEquals(3, subject.getSbpr());
        assertEquals(4, subject.getVpt());
        assertEquals(5, subject.getGas());
        assertEquals(6, subject.getRbs());
        assertEquals(7, subject.getSbs());
        assertEquals(8, subject.getNetworkRbs());
    }

    @Test
    void toStringWorks() {
        final var desired = "UsageAccumulator{universalBpt=1, networkVpt=4, networkRbh=1, nodeBpr=2,"
                + " nodeSbpr=3, nodeVpt=0, serviceSbh=1, serviceRbh=1, gas=5, rbs=6}";

        // given:
        subject.addBpt(1);
        subject.addBpr(2);
        subject.addSbpr(3);
        subject.addVpt(4);
        subject.addGas(5);
        subject.addRbs(6);
        subject.addSbs(7);
        subject.addNetworkRbs(8);

        // expect:
        assertEquals(desired, subject.toString());
    }
}
