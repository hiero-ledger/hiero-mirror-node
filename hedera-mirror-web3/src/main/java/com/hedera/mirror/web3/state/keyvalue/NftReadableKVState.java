// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node The object, which is read from DB is converted to the PBJ generated format, so that it can properly be
 * utilized by the hedera app components
 */
@Named
public class NftReadableKVState extends AbstractReadableKVState<NftID, Nft> {

    public static final String KEY = "NFTS";
    private final NftRepository nftRepository;

    public NftReadableKVState(@Nonnull NftRepository nftRepository) {
        super(KEY);
        this.nftRepository = nftRepository;
    }

    @Override
    protected Nft readFromDataSource(@Nonnull final NftID key) {
        if (key.tokenId() == null) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var nftId = EntityIdUtils.toEntityId(key.tokenId()).getId();
        return timestamp
                .map(t -> nftRepository.findActiveByIdAndTimestamp(nftId, key.serialNumber(), t))
                .orElseGet(() -> nftRepository.findActiveById(nftId, key.serialNumber()))
                .map(nft -> mapToNft(nft, key.tokenId()))
                .orElse(null);
    }

    private Nft mapToNft(final com.hedera.mirror.common.domain.token.Nft nft, final TokenID tokenID) {
        return Nft.newBuilder()
                .metadata(Bytes.wrap(nft.getMetadata()))
                .mintTime(convertToTimestamp(nft.getCreatedTimestamp()))
                .nftId(new NftID(tokenID, nft.getSerialNumber()))
                .ownerId(EntityIdUtils.toAccountId(nft.getAccountId()))
                .spenderId(EntityIdUtils.toAccountId(nft.getSpender()))
                .build();
    }
}
