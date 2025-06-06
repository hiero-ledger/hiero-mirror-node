// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.fees;

public class SigValueObj {

    public SigValueObj(int totalSigCount, int payerAcctSigCount, int signatureSize) {
        super();
        this.totalSigCount = totalSigCount;
        this.payerAcctSigCount = payerAcctSigCount;
        this.signatureSize = signatureSize;
    }

    private int totalSigCount;
    private int payerAcctSigCount;
    private int signatureSize;

    public int getTotalSigCount() {
        return totalSigCount;
    }

    public int getPayerAcctSigCount() {
        return payerAcctSigCount;
    }

    public int getSignatureSize() {
        return signatureSize;
    }
}
