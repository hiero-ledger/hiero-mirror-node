// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.balance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("account_balance")
public class AccountBalance implements Persistable<AccountBalance.Id>, StreamItem {

    private long balance;

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    @org.springframework.data.annotation.Id
    private EntityId accountId;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @MappedCollection(idColumn = "account_id", keyColumn = "consensus_timestamp")
    private List<TokenBalance> tokenBalances = new ArrayList<>();

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusTimestamp, accountId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 1345295043157256768L;
        private long consensusTimestamp;
        private EntityId accountId;
    }
}
