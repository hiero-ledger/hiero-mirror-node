// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.addressbook;

import java.security.PublicKey;
import java.util.Objects;
import lombok.Value;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.entity.EntityId;

@Value
final class ConsensusNodeWrapper implements ConsensusNode {

    private final AddressBookEntry addressBookEntry;
    private final NodeStake nodeStake;
    private final long nodeCount;
    private final long totalStake;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConsensusNode node) {
            return Objects.equals(node.getNodeId(), getNodeId());
        }

        return false;
    }

    public long getNodeId() {
        return addressBookEntry.getNodeId();
    }

    public EntityId getNodeAccountId() {
        return addressBookEntry.getNodeAccountId();
    }

    @Override
    public PublicKey getPublicKey() {
        return addressBookEntry.getPublicKeyObject();
    }

    @Override
    public long getStake() {
        if (totalStake > 0) {
            return nodeStake != null ? nodeStake.getStake() : 0L;
        } else {
            return 1L;
        }
    }

    public long getTotalStake() {
        return totalStake > 0 ? totalStake : nodeCount;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getNodeId());
    }

    @Override
    public String toString() {
        return String.valueOf(getNodeId());
    }
}
