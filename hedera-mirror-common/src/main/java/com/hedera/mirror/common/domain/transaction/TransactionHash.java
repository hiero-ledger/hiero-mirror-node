// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.primitives.Shorts;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
public class TransactionHash implements Persistable<byte[]> {
    // The random nature of the choice of sharded tables makes this test fail, so only insert to a single shard.
    public static final int V1_SHARD_COUNT = 1;

    private long consensusTimestamp;

    @Setter(value = AccessLevel.NONE)
    private short distributionId;

    @Id
    private byte[] hash;

    private long payerAccountId;

    @Builder
    public TransactionHash(long consensusTimestamp, byte[] hash, long payerAccountId) {
        this.consensusTimestamp = consensusTimestamp;
        setHash(hash);
        this.payerAccountId = payerAccountId;
    }

    @JsonIgnore
    @Override
    public byte[] getId() {
        return hash;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    public int calculateV1Shard() {
        return Math.floorMod(hash[0], V1_SHARD_COUNT);
    }

    public boolean hashIsValid() {
        return this.hash != null && hash.length > 0;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
        if (ArrayUtils.isNotEmpty(hash) && hash.length >= 2) {
            this.distributionId = Shorts.fromByteArray(hash);
        }
    }
}
