// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.balance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Persistable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Upsertable
@NullMarked
public class TokenBalanceChange implements Persistable<AbstractTokenAccount.Id> {

    @EmbeddedId
    @JsonUnwrapped
    private TokenAccount.Id id;

    @Override
    @JsonIgnore
    public boolean isNew() {
        return true;
    }
}
