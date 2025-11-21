// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.balance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.Upsertable;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Persistable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Upsertable
@NullMarked
public class AccountBalanceChange implements Persistable<Long> {

    @Id
    private Long accountId;

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update balances and use a natural ID, avoid Hibernate querying before insert
    }

    @Override
    @JsonIgnore
    public Long getId() {
        return accountId;
    }
}
