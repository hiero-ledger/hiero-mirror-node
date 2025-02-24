// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto.helpers;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hedera.services.utils.IdUtils.asModelId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllowanceHelpersTest {
    @Mock
    private Store store;

    final Token token = mock(Token.class);
    final Account treasury = mock(Account.class);
    final Id ownerId = asModelId("0.0.123");
    final Id spenderId = asModelId("0.0.124");
    final Id tokenId = asModelId("0.0.125");
    final long serial1 = 1L;
    final long serial2 = 2L;

    @Test
    void failsToUpdateSpenderIfWrongOwner() {
        final UniqueToken nft1 = new UniqueToken(tokenId, serial1, null, ownerId, null, null);
        final UniqueToken nft2 = new UniqueToken(tokenId, serial2, null, spenderId, null, null);
        final var serials = List.of(serial1, serial2);

        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(ownerId);

        final var ex = assertThrows(
                InvalidTransactionException.class, () -> updateSpender(store, ownerId, spenderId, tokenId, serials));

        assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, ex.getResponseCode());
    }

    @Test
    void updatesSpenderAsExpected() {
        final UniqueToken nft1 = new UniqueToken(tokenId, serial1, null, ownerId, null, null);
        final UniqueToken nft2 = new UniqueToken(tokenId, serial2, null, ownerId, null, null);

        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(ownerId);

        final var updatedNfts = updateSpender(store, ownerId, spenderId, tokenId, List.of(serial1, serial2));

        assertEquals(spenderId, updatedNfts.get(0).getSpender());
        assertEquals(spenderId, updatedNfts.get(1).getSpender());
    }
}
