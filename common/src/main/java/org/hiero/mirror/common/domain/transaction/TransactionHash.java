// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Arrays;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
public class TransactionHash implements Persistable<byte[]> {
    public static final int V1_SHARD_COUNT = 32;

    private long consensusTimestamp;

    @Id
    private byte[] hash;

    private byte[] hashSuffix;

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
        return this.hash != null && (hash.length == 32 || hash.length == 48);
    }

    public void setHash(byte[] hash) {
        if (ArrayUtils.isNotEmpty(hash) && hash.length == 32) {
            this.hash = hash;
        } else if (ArrayUtils.isNotEmpty(hash) && hash.length == 48) {
            this.hash = Arrays.copyOfRange(hash, 0, 32);
            this.hashSuffix = Arrays.copyOfRange(hash, 32, hash.length);
        }
    }
}
