// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.sql.Date;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractEntity implements History {

    public static final long ACCOUNT_ID_CLEARED = 0L;
    public static final long DEFAULT_EXPIRY_TIMESTAMP =
            TimeUnit.MILLISECONDS.toNanos(Date.valueOf("2100-1-1").getTime());
    public static final long NODE_ID_CLEARED = -1L;

    private static final String CLEAR_PUBLIC_KEY = StringUtils.EMPTY;

    @ToString.Exclude
    @UpsertColumn(updatable = false)
    private byte[] alias;

    private Long autoRenewAccountId;

    private Long autoRenewPeriod;

    @UpsertColumn(coalesce = """
                            case when coalesce(e_type, type) in (''ACCOUNT'', ''CONTRACT'') then coalesce(e_{0}, 0) + coalesce({0}, 0)
                                 when e_{0} is not null then e_{0} + coalesce({0}, 0)
                            end""")
    private Long balance;

    private Long balanceTimestamp;

    @UpsertColumn(updatable = false)
    private Long createdTimestamp;

    private Boolean declineReward;

    private Boolean deleted;

    @ToString.Exclude
    private byte[] delegationAddress;

    @UpsertColumn(coalesce = """
                            case when coalesce(e_type, type) = ''ACCOUNT'' then coalesce({0}, e_{0}, {1})
                                 else coalesce({0}, e_{0})
                            end""")
    private Long ethereumNonce;

    @ToString.Exclude
    @UpsertColumn(updatable = false)
    private byte[] evmAddress;

    private Long expirationTimestamp;

    @Id
    private Long id;

    @ToString.Exclude
    private byte[] key;

    private Integer maxAutomaticTokenAssociations;

    private String memo;

    @UpsertColumn(updatable = false)
    private Long num;

    private EntityId obtainerId;

    private Boolean permanentRemoval;

    private EntityId proxyAccountId;

    @ToString.Exclude
    @UpsertColumn(coalesce = """
                            case when {0} is not null and length({0}) = 0 then null
                                 else coalesce({0}, e_{0}, null)
                            end""")
    private String publicKey;

    @UpsertColumn(updatable = false)
    private Long realm;

    private Boolean receiverSigRequired;

    @UpsertColumn(updatable = false)
    private Long shard;

    private Long stakedAccountId;

    private Long stakedNodeId;

    private Long stakePeriodStart;

    private Range<Long> timestampRange;

    // Hidden from Jackson so batch COPY serialization only sees the "type" property matching the db column
    @JsonIgnore
    @Column("type")
    private EntityType entityType;

    public EntityType getType() {
        return entityType;
    }

    public void setType(EntityType type) {
        this.entityType = type;
    }

    public void addBalance(Long balance) {
        if (balance == null) {
            return;
        }

        if (this.balance == null) {
            this.balance = balance;
        } else {
            this.balance += balance;
        }
    }

    /**
     * Sets the entity's key. Note publicKey is extracted from the key as a side effect. A null key indicates there
     * is no key / public key change and publicKey is set to null as well. For an empty key or unparsable key, publicKey
     * is set to the sentinel value, an empty string, and the upsert SQL will clear the public_key column by setting it
     * to null.
     *
     * @param key - The protobuf key bytes
     */
    public void setKey(byte[] key) {
        this.key = key;
        publicKey = getPublicKey(key);
    }

    public void setMemo(String memo) {
        this.memo = DomainUtils.sanitize(memo);
    }

    public EntityId toEntityId() {
        return EntityId.of(shard, realm, num);
    }

    @JsonIgnore
    public long getEffectiveExpiration() {
        if (expirationTimestamp != null) {
            return expirationTimestamp;
        }

        if (createdTimestamp != null && autoRenewPeriod != null) {
            return createdTimestamp + TimeUnit.SECONDS.toNanos(autoRenewPeriod);
        }

        return DEFAULT_EXPIRY_TIMESTAMP;
    }

    private static String getPublicKey(@Nullable byte[] protobufKey) {
        if (protobufKey == null) {
            return null;
        }

        var publicKey = DomainUtils.getPublicKey(protobufKey);
        return publicKey != null ? publicKey : CLEAR_PUBLIC_KEY;
    }

    @SuppressWarnings("java:S1610")
    // Necessary since Lombok doesn't use our setters for builders
    public abstract static class AbstractEntityBuilder<
            C extends AbstractEntity, B extends AbstractEntityBuilder<C, B>> {
        public B key(byte[] key) {
            this.key = key;
            this.publicKey = getPublicKey(key);
            return self();
        }

        public B memo(String memo) {
            this.memo = DomainUtils.sanitize(memo);
            return self();
        }

        public B type(EntityType entityType) {
            this.entityType = entityType;
            return self();
        }
    }
}
