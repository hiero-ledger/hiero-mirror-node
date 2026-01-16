// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.balance;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.util.Assert;

@Named
@RequiredArgsConstructor
public class AccountBalanceBuilder {

    private final DomainBuilder domainBuilder;

    public Builder accountBalance() {
        return accountBalance(domainBuilder.timestamp());
    }

    public Builder accountBalance(long consensusTimestamp) {
        return new Builder(consensusTimestamp);
    }

    public static class Builder {

        private final long consensusTimestamp;
        private final List<TokenBalance> tokenBalances = new ArrayList<>();

        private EntityId accountId;
        private long balance;

        private Builder(long consensusTimestamp) {
            this.consensusTimestamp = consensusTimestamp;
        }

        public Builder accountId(long accountId) {
            return accountId(EntityId.of(accountId));
        }

        public Builder accountId(EntityId accountId) {
            Assert.isNull(this.accountId, "AccountId is already set");
            this.accountId = accountId;
            return this;
        }

        public Builder balance(long balance) {
            this.balance = balance;
            return this;
        }

        public Builder tokenBalance(long balance, long tokenId) {
            return tokenBalance(balance, EntityId.of(tokenId));
        }

        public Builder tokenBalance(long balance, EntityId tokenId) {
            Assert.notNull(this.accountId, "Must set accountId");
            var tokenBalance = TokenBalance.builder()
                    .balance(balance)
                    .id(new TokenBalance.Id(consensusTimestamp, accountId, tokenId))
                    .build();
            tokenBalances.add(tokenBalance);
            return this;
        }

        public AccountBalance build() {
            return AccountBalance.builder()
                    .balance(balance)
                    .id(new AccountBalance.Id(consensusTimestamp, accountId))
                    .tokenBalances(tokenBalances)
                    .build();
        }
    }
}
