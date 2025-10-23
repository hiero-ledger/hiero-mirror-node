// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.node.app.service.token.TokenService;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.TokenAirdropRepository;

@Named
public class AirdropsReadableKVState extends AbstractReadableKVState<PendingAirdropId, AccountPendingAirdrop> {

    public static final String KEY = "PENDING_AIRDROPS";
    private final TokenAirdropRepository tokenAirdropRepository;

    protected AirdropsReadableKVState(final TokenAirdropRepository tokenAirdropRepository) {
        super(TokenService.NAME, KEY);
        this.tokenAirdropRepository = tokenAirdropRepository;
    }

    @Override
    protected AccountPendingAirdrop readFromDataSource(@Nonnull PendingAirdropId key) {
        final var senderId = toEntityId(key.senderId()).getId();
        final var receiverId = toEntityId(key.receiverId()).getId();
        final var tokenId = toEntityId(
                key.hasNonFungibleToken() ? key.nonFungibleToken().tokenId() : key.fungibleTokenType())
                .getId();
        final var serialNumber =
                key.hasNonFungibleToken() ? key.nonFungibleToken().serialNumber() : 0L;
        final var timestamp = ContractCallContext.get().getTimestamp();

        return timestamp
                .map(t -> tokenAirdropRepository.findByIdAndTimestamp(senderId, receiverId, tokenId, serialNumber, t))
                .orElseGet(() -> tokenAirdropRepository.findById(senderId, receiverId, tokenId, serialNumber))
                .map(tokenAirdrop -> key.hasNonFungibleToken()
                        ? AccountPendingAirdrop.DEFAULT
                        : mapToAccountPendingAirdrop(tokenAirdrop.getAmount()))
                .orElse(null);
    }

    private AccountPendingAirdrop mapToAccountPendingAirdrop(final long amount) {
        return AccountPendingAirdrop.newBuilder()
                .pendingAirdropValue(mapToPendingAirdropValue(amount))
                .build();
    }

    private PendingAirdropValue mapToPendingAirdropValue(final long amount) {
        return PendingAirdropValue.newBuilder().amount(amount).build();
    }
}
