// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.google.common.collect.Range;
import java.sql.Date;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    private byte[] alias;

    private Long autoRenewAccountId;

    private Long autoRenewPeriod;

    @UpsertColumn(coalesce = """
                            case when coalesce(e_type, type) in (''ACCOUNT'', ''CONTRACT'') then coalesce(e_{0}, 0) + coalesce({0}, 0)
                                 when e_{0} is not null then e_{0} + coalesce({0}, 0)
                            end""")
    private Long balance;

    private Long balanceTimestamp;

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
    private byte[] evmAddress;

    private Long expirationTimestamp;

    @Id
    private Long id;

    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    private byte[] key;

    private Integer maxAutomaticTokenAssociations;

    @Setter(AccessLevel.NONE)
    private String memo;

    private Long num;

    // Converter removed. Handled by global EntityId Reading/Writing converters.
    private EntityId obtainerId;

    private Boolean permanentRemoval;

    private EntityId proxyAccountId;

    @ToString.Exclude
    @UpsertColumn(coalesce = """
                            case when {0} is not null and length({0}) = 0 then null
                                 else coalesce({0}, e_{0}, null)
                            end""")
    private String publicKey;

    private Long realm;

    private Boolean receiverSigRequired;

    private Long shard;

    private Long stakedAccountId;

    private Long stakedNodeId;

    private Long stakePeriodStart;

    // JDBC: Persisted via RangeToPGobjectWritingConverter / PGobjectToRangeReadingConverter
    private Range<Long> timestampRange;

    /**
     * Stored as Postgres {@code entity_type}. See {@link PostgresEntityType} — do not map {@code PGobject} directly on the aggregate.
     */
    @Column("type")
    private PostgresEntityType entityTypePg;

    public EntityType getType() {
        return entityTypePg == null ? null : entityTypePg.getEntityType();
    }

    public void setType(EntityType entityType) {
        entityTypePg = PostgresEntityType.of(entityType);
    }

    private static String getPublicKey(@Nullable byte[] protobufKey) {
        if (protobufKey == null) {
            return null;
        }

        var publicKey = DomainUtils.getPublicKey(protobufKey);
        return publicKey != null ? publicKey : CLEAR_PUBLIC_KEY;
    }

    public void setMemo(String memo) {
        this.memo = DomainUtils.sanitize(memo);
    }

    public void setKey(byte[] key) {
        this.key = key;
        this.publicKey = getPublicKey(key);
    }

    public void addBalance(Long delta) {
        if (balance == null && delta == null) {
            return;
        }
        long base = balance != null ? balance : 0L;
        long d = delta != null ? delta : 0L;
        setBalance(base + d);
    }

    @SuppressWarnings("java:S1610")
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

        /** Same as {@link #setType(EntityType)} for SuperBuilder / DomainBuilder ({@code .type(ACCOUNT)}). */
        public B type(EntityType entityType) {
            this.entityTypePg = PostgresEntityType.of(entityType);
            return self();
        }
    }
}
