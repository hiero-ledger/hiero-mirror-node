// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static com.hedera.services.utils.EntityIdUtils.idFromEncodedId;
import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;

import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import org.hiero.mirror.web3.repository.NftRepository;
import org.jspecify.annotations.NonNull;

@Named
@RequiredArgsConstructor
public class UniqueTokenDatabaseAccessor extends DatabaseAccessor<Object, UniqueToken> {
    private final NftRepository nftRepository;

    @Override
    public @NonNull Optional<UniqueToken> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof NftId nftId) {
            final var tokenId = EntityIdUtils.entityIdFromNftId(nftId).getId();
            return timestamp
                    .map(t -> nftRepository.findActiveByIdAndTimestamp(tokenId, nftId.serialNo(), t))
                    .orElseGet(() -> nftRepository.findActiveById(tokenId, nftId.serialNo()))
                    .map(this::mapNftToUniqueToken);
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(UniqueToken.class.getTypeName(), key.getClass().getTypeName()));
    }

    private UniqueToken mapNftToUniqueToken(Nft nft) {
        var tokenId = idFromEntityId(EntityId.of(nft.getTokenId()));
        return new UniqueToken(
                tokenId,
                nft.getSerialNumber(),
                mapNanosToRichInstant(nft.getCreatedTimestamp()),
                idFromEntityId(nft.getAccountId()),
                idFromEncodedId(nft.getSpender()),
                nft.getMetadata());
    }

    private RichInstant mapNanosToRichInstant(Long nanos) {
        if (nanos == null) {
            return RichInstant.MISSING_INSTANT;
        }

        return RichInstant.fromJava(Instant.ofEpochSecond(0, nanos));
    }
}
