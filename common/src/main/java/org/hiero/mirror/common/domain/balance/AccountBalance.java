// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.balance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
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
import org.springframework.data.domain.Persistable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@NamedEntityGraph(name = "AccountBalance.tokenBalances", attributeNodes = @NamedAttributeNode("tokenBalances"))
public class AccountBalance implements Persistable<AccountBalance.Id>, StreamItem {

    private long balance;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @OneToMany(
            cascade = {CascadeType.ALL},
            orphanRemoval = true)
    // set updatable = false to prevent additional hibernate query
    @JoinColumn(name = "accountId", updatable = false)
    @JoinColumn(name = "consensusTimestamp", updatable = false)
    private List<TokenBalance> tokenBalances = new ArrayList<>();

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update balances and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1345295043157256768L;

        private long consensusTimestamp;

        private EntityId accountId;
    }
}
