// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table
@NoArgsConstructor
public class EthereumTransaction implements Persistable<Long> {

    @JsonIgnore
    @Column("access_list")
    private AccessListHolder accessListColumn;

    @JsonIgnore
    @Column("authorization_list")
    private AuthorizationListHolder authorizationListColumn;

    @ToString.Exclude
    private byte[] callData;

    private EntityId callDataId;

    @ToString.Exclude
    private byte[] chainId;

    @Id
    private long consensusTimestamp;

    @ToString.Exclude
    private byte[] data;

    private Long gasLimit;

    @ToString.Exclude
    private byte[] gasPrice;

    @ToString.Exclude
    private byte[] hash;

    @ToString.Exclude
    private byte[] maxFeePerGas;

    private Long maxGasAllowance;

    @ToString.Exclude
    private byte[] maxPriorityFeePerGas;

    private Long nonce;

    private EntityId payerAccountId;

    private Integer recoveryId;

    @Column("signature_r")
    @ToString.Exclude
    private byte[] signatureR;

    @Column("signature_s")
    @ToString.Exclude
    private byte[] signatureS;

    @Column("signature_v")
    @ToString.Exclude
    private byte[] signatureV;

    @ToString.Exclude
    private byte[] toAddress;

    private Integer type;

    @ToString.Exclude
    private byte[] value;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<AccessList> getAccessList() {
        return accessListColumn == null ? null : accessListColumn.items();
    }

    public void setAccessList(List<AccessList> value) {
        this.accessListColumn = AccessListHolder.of(value);
    }

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<Authorization> getAuthorizationList() {
        return authorizationListColumn == null ? null : authorizationListColumn.items();
    }

    public void setAuthorizationList(List<Authorization> value) {
        this.authorizationListColumn = AuthorizationListHolder.of(value);
    }

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public TransactionHash toTransactionHash() {
        return TransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(hash)
                .payerAccountId(payerAccountId != null ? payerAccountId.getId() : null)
                .build();
    }

    public static class EthereumTransactionBuilder {
        public EthereumTransactionBuilder accessList(List<AccessList> value) {
            this.accessListColumn = AccessListHolder.of(value);
            return this;
        }

        public EthereumTransactionBuilder authorizationList(List<Authorization> value) {
            this.authorizationListColumn = AuthorizationListHolder.of(value);
            return this;
        }
    }
}
