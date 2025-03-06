// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.MiscUtils.perm64;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces the risk of hash collisions in structured data
 * using this type, when compared to the {@code java.lang.Integer} boxed type.
 */
public class EntityNum implements Comparable<EntityNum> {
    public static final EntityNum MISSING_NUM = new EntityNum(EntityId.EMPTY);
    private final EntityId entityId;

    private EntityNum(final EntityId entityId) {
        this.entityId = entityId;
    }

    public EntityNum(final int value) {
        this.entityId = EntityId.of(value);
    }

    public static EntityNum fromEvmAddress(final Address address) {
        return new EntityNum(DomainUtils.fromEvmAddress(address.toArrayUnsafe()));
    }

    public static EntityNum fromInt(final int i) {
        return new EntityNum(i);
    }

    public static EntityNum fromLong(final long l) {
        return new EntityNum(EntityId.of(l));
    }

    public static EntityNum fromAccountId(final AccountID grpc) {
        // Should this remain restricted?
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getAccountNum());
    }

    public static EntityNum fromTokenId(final TokenID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getTokenNum());
    }

    static boolean areValidNums(final long shard, final long realm) {
        return shard == 0 && realm == 0;
    }

    public static EntityNum fromId(Id id) {
        return new EntityNum(EntityId.of(id.shard(), id.realm(), id.num()));
    }

    public long getId() {
        return entityId.getId();
    }

    @Override
    public int hashCode() {
        return (int) perm64(entityId.getId());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityNum.class != o.getClass()) {
            return false;
        }

        final var that = (EntityNum) o;

        return this.entityId == that.entityId;
    }

    @Override
    public String toString() {
        var entityString = String.format("%d.%d.%d", entityId.getShard(), entityId.getRealm(), entityId.getNum());
        return "EntityNum{" + "value=" + entityString + '}';
    }

    @Override
    public int compareTo(@NonNull final EntityNum that) {
        return this.entityId.compareTo(that.entityId);
    }

    public AccountID scopedAccountWith() {
        return AccountID.newBuilder()
                .setShardNum(entityId.getShard())
                .setRealmNum(entityId.getRealm())
                .setAccountNum(entityId.getNum())
                .build();
    }

    public TokenID toTokenId() {
        return TokenID.newBuilder()
                .setShardNum(entityId.getShard())
                .setRealmNum(entityId.getRealm())
                .setTokenNum(entityId.getNum())
                .build();
    }
}
