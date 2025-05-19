// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.fees;

public class FeeObject {

    private long nodeFee;
    private long networkFee;
    private long serviceFee;

    public FeeObject(long nodeFee, long networkFee, long serviceFee) {
        this.nodeFee = nodeFee;
        this.networkFee = networkFee;
        this.serviceFee = serviceFee;
    }

    public long getNodeFee() {
        return nodeFee;
    }

    public long getNetworkFee() {
        return networkFee;
    }

    public long getServiceFee() {
        return serviceFee;
    }

    @Override
    public String toString() {
        return "FeeObject{" + "nodeFee=" + nodeFee + ", networkFee=" + networkFee + ", serviceFee=" + serviceFee + '}';
    }
}
