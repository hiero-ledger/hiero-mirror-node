// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.sql.Date;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.Nullable;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractEntity implements History {

    public static final long ACCOUNT_ID_CLEARED = 0L;
    public static final long DEFAULT_EXPIRY_TIMESTAMP =
            TimeUnit.MILLISECONDS.toNanos(Date.valueOf("2100-1-1").getTime());
    public static final long NODE_ID_CLEARED = -1L;

    private static final String CLEAR_PUBLIC_KEY = StringUtils.EMPTY;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] alias;

    private Long autoRenewAccountId;

    private Long autoRenewPeriod;

    @UpsertColumn(
            coalesce =
                    """
                            case when coalesce(e_type, type) in (''ACCOUNT'', ''CONTRACT'') then coalesce(e_{0}, 0) + coalesce({0}, 0)
                                 when e_{0} is not null then e_{0} + coalesce({0}, 0)
                            end""")
    private Long balance;

    private Long balanceTimestamp;

    @Column(updatable = false)
    private Long createdTimestamp;

    private Boolean declineReward;

    private Boolean deleted;

    @UpsertColumn(
            coalesce =
                    """
                            case when coalesce(e_type, type) = ''ACCOUNT'' then coalesce({0}, e_{0}, {1})
                                 else coalesce({0}, e_{0})
                            end""")
    private Long ethereumNonce;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] evmAddress;

    private Long expirationTimestamp;

    @Id
    private Long id;

    @ToString.Exclude
    private byte[] key;

    private Integer maxAutomaticTokenAssociations;

    private String memo;

    @Column(updatable = false)
    private Long num;

    private EntityId obtainerId;

    private Boolean permanentRemoval;

    private EntityId proxyAccountId;

    @ToString.Exclude
    @UpsertColumn(
            coalesce =
                    """
                            case when {0} is not null and length({0}) = 0 then null
                                 else coalesce({0}, e_{0}, null)
                            end""")
    private String publicKey;

    @Column(updatable = false)
    private Long realm;

    private Boolean receiverSigRequired;

    @Column(updatable = false)
    private Long shard;

    private Long stakedAccountId;

    private Long stakedNodeId;

    private Long stakePeriodStart;

    private Range<Long> timestampRange;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EntityType type;

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
    }
}
