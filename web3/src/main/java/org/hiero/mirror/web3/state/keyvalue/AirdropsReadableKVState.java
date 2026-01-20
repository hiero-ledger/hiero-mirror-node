// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.token.impl.schemas.V0530TokenSchema.AIRDROPS_STATE_ID;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.node.app.service.token.TokenService;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.TokenAirdropRepository;
import org.jspecify.annotations.NonNull;

@Named
final class AirdropsReadableKVState extends AbstractReadableKVState<PendingAirdropId, AccountPendingAirdrop> {

    public static final int STATE_ID = AIRDROPS_STATE_ID;

    private final TokenAirdropRepository tokenAirdropRepository;

    protected AirdropsReadableKVState(final TokenAirdropRepository tokenAirdropRepository) {
        super(TokenService.NAME, STATE_ID);
        this.tokenAirdropRepository = tokenAirdropRepository;
    }

    @Override
    protected AccountPendingAirdrop readFromDataSource(@NonNull PendingAirdropId key) {
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

    @Override
    public String getServiceName() {
        return TokenService.NAME;
    }
}
