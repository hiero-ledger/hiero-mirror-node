// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.NFTS_STATE_ID;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.AbstractToken;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
import org.jspecify.annotations.NonNull;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node The object, which is read from DB is converted to the PBJ generated format, so that it can properly be
 * utilized by the hedera app components
 */
@Named
final class NftReadableKVState extends AbstractReadableKVState<NftID, Nft> {

    public static final int STATE_ID = NFTS_STATE_ID;
    private final NftRepository nftRepository;
    private final TokenRepository tokenRepository;

    public NftReadableKVState(@NonNull NftRepository nftRepository, @NonNull TokenRepository tokenRepository) {
        super(TokenService.NAME, STATE_ID);
        this.nftRepository = nftRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected Nft readFromDataSource(@NonNull final NftID key) {
        if (key.tokenId() == null) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var nftId = EntityIdUtils.toEntityId(key.tokenId()).getId();
        final var tokenTreasury = getTokenTreasury(nftId, timestamp);

        return timestamp
                .map(t -> nftRepository.findActiveByIdAndTimestamp(nftId, key.serialNumber(), t))
                .orElseGet(() -> nftRepository.findActiveById(nftId, key.serialNumber()))
                .map(nft -> mapToNft(nft, key.tokenId(), tokenTreasury))
                .orElse(null);
    }

    private Nft mapToNft(
            final org.hiero.mirror.common.domain.token.Nft nft,
            final TokenID tokenID,
            final EntityId treasuryAccountId) {
        return Nft.newBuilder()
                .metadata(Bytes.wrap(nft.getMetadata()))
                .mintTime(convertToTimestamp(nft.getCreatedTimestamp()))
                .nftId(new NftID(tokenID, nft.getSerialNumber()))
                .ownerId(getOwnerId(nft.getAccountId(), treasuryAccountId))
                .spenderId(EntityIdUtils.toAccountId(nft.getSpender()))
                .build();
    }

    private EntityId getTokenTreasury(final long nftId, Optional<Long> timestamp) {
        return timestamp
                .flatMap(t -> tokenRepository.findByTokenIdAndTimestamp(nftId, t))
                .or(() -> tokenRepository.findById(nftId))
                .map(AbstractToken::getTreasuryAccountId)
                .orElse(null);
    }

    private AccountID getOwnerId(final EntityId accountId, final EntityId treasuryAccountId) {
        if (accountId == null || accountId.equals(treasuryAccountId)) {
            return null;
        }
        return EntityIdUtils.toAccountId(accountId);
    }

    @Override
    public Integer getId() {
        return STATE_ID;
    }

    @Override
    public String getServiceName() {
        return TokenService.NAME;
    }
}
