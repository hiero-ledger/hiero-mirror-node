// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.projections;

public interface EntitySnapshot {

    byte[] getAlias();

    Long getEthereumNonce();

    byte[] getEvmAddress();

    long getId();

    String getType();
}
