/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import jakarta.persistence.Transient;
import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Value;

/**
 * Common encapsulation for a Hedera entity identifier.
 */
@Value
public final class EntityId implements Serializable, Comparable<EntityId> {

    public static final EntityId EMPTY = new EntityId(0L);

    /*
     * Indicates a domain entity ID is not being set (to null or non-null) in the current operation and that
     * the existing DB column value is to be preserved. This is reflected as the value of -1 that can be
     * referenced within the @UpsertColumn syntax. See the AbstractNft delegatingSpender and spender
     * fields as examples. In this case, this sentinel value is set in TokenUpdateNftsTransactionHandler.
     */
    public static final EntityId UNSET = new EntityId();

    static final int NUM_BITS = 38;
    static final int REALM_BITS = 16;
    static final int SHARD_BITS = 10;

    private static final long NUM_MASK = (1L << NUM_BITS) - 1;
    private static final long REALM_MASK = (1L << REALM_BITS) - 1;
    private static final long SHARD_MASK = (1L << SHARD_BITS) - 1;

    private static final String CACHE_DEFAULT = "expireAfterAccess=60m,maximumSize=500000,recordStats";
    private static final String CACHE_PROPERTY = "HEDERA_MIRROR_COMMON_CACHE_ENTITYID";
    private static final String CACHE_SPEC = System.getProperty(CACHE_PROPERTY, CACHE_DEFAULT);
    private static final Cache<Long, EntityId> CACHE = Caffeine.from(CACHE_SPEC).build();

    private static final Comparator<EntityId> COMPARATOR =
            Comparator.nullsFirst(Comparator.comparingLong(EntityId::getId));
    private static final Range<Long> DEFAULT_RANGE = Range.atLeast(0L);
    private static final String DOT = ".";
    private static final Splitter SPLITTER = Splitter.on('.').omitEmptyStrings().trimResults();

    @Serial
    private static final long serialVersionUID = 1427649605832330197L;

    @JsonValue
    private final long id;

    private EntityId(long id) {
        if (id < 0) {
            throw new InvalidEntityException("Entity ID can not be negative: " + id);
        }

        this.id = id;
    }

    // Used only to construct constant UNSET above
    private EntityId() {
        this.id = -1L;
    }

    /**
     * Encodes given shard, realm, and num into 8 bytes long value.
     * <p/>
     * Only 63 bits (excluding the signed bit) are used for encoding to facilitate easy encoding/decoding using mathematical
     * operations. This is necessary because JavaScript's support for bitwise operations is limited (truncating numbers to
     * 32 bits internally before performing bitwise operations).
     * <p/>
     * <b>Encoding Format:</b> <br/>
     * - The first bit (sign bit) is set to 0. <br/>
     * - The next 10 bits are reserved for the shard, followed by 16 bits for the realm, and then 37 bits for the entity num. <br/>
     * <p/>
     * <b>Supported Ranges:</b> <br/>
     * - Shard: 0 - 1023 (10 bits) <br/>
     * - Realm: 0 - 65535 (16 bits) <br/>
     * - Entity Num: 0 - 137438953471 (37 bits) <br/>
     * <p/>
     * Placing the entity num at the end ensures that encoded IDs ≤ 137438953471 remain human-readable.
     */
    private static long encode(long shard, long realm, long num) {
        if (shard > SHARD_MASK || shard < 0 || realm > REALM_MASK || realm < 0 || num > NUM_MASK || num < 0) {
            throw new InvalidEntityException("Invalid entity ID: " + shard + "." + realm + "." + num);
        }

        if (shard == 0 && realm == 0) {
            return num;
        }

        return (num & NUM_MASK) | (realm & REALM_MASK) << NUM_BITS | (shard & SHARD_MASK) << (REALM_BITS + NUM_BITS);
    }

    public static EntityId of(AccountID accountID) {
        return of(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
    }

    public static EntityId of(ContractID contractID) {
        return of(contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum());
    }

    public static EntityId of(FileID fileID) {
        return of(fileID.getShardNum(), fileID.getRealmNum(), fileID.getFileNum());
    }

    public static EntityId of(TopicID topicID) {
        return of(topicID.getShardNum(), topicID.getRealmNum(), topicID.getTopicNum());
    }

    public static EntityId of(TokenID tokenID) {
        return of(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum());
    }

    public static EntityId of(ScheduleID scheduleID) {
        return of(scheduleID.getShardNum(), scheduleID.getRealmNum(), scheduleID.getScheduleNum());
    }

    public static EntityId of(String entityId) {
        List<Long> parts = SPLITTER.splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .toList();

        if (parts.size() != 3) {
            throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        }

        return of(parts.get(0), parts.get(1), parts.get(2));
    }

    public static EntityId of(long shard, long realm, long num) {
        long id = encode(shard, realm, num);
        return of(id);
    }

    public static EntityId of(long id) {
        if (id == 0) {
            return EMPTY;
        }

        return CACHE.get(id, k -> new EntityId(id));
    }

    public static boolean isEmpty(EntityId entityId) {
        return entityId == null || EMPTY.equals(entityId);
    }

    @Transient
    public long getNum() {
        return id & NUM_MASK;
    }

    @Transient
    public long getRealm() {
        return (id >> NUM_BITS) & REALM_MASK;
    }

    @Transient
    public long getShard() {
        return (id >>> (NUM_BITS + REALM_BITS) & SHARD_MASK);
    }

    public Entity toEntity() {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setNum(getNum());
        entity.setRealm(getRealm());
        entity.setShard(getShard());
        entity.setTimestampRange(DEFAULT_RANGE);
        return entity;
    }

    @Override
    public int compareTo(EntityId other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return getShard() + DOT + getRealm() + DOT + getNum();
    }
}
