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
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("account_balance")
public class AccountBalance implements Persistable<AccountBalance.Id>, StreamItem {

    private long balance;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @Transient
    private List<TokenBalance> tokenBalances = new ArrayList<>();

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @JsonIgnore
    @Override
    public Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update balances and use a natural ID, avoid Hibernate querying before insert
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
