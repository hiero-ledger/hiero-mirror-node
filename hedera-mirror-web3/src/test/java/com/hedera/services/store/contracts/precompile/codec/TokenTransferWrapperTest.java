// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.store.contracts.precompile.TokenTransferWrapper;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenTransferWrapperTest {
    @Test
    void translatesNftExchangesAsExpected() {
        final var inputExchanges = wellKnownExchanges();
        final var nftSubject = new TokenTransferWrapper(inputExchanges, Collections.emptyList());

        final var builder = nftSubject.asGrpcBuilder();
        assertEquals(token, builder.getToken());
        final var exchanges = builder.getNftTransfersList();
        assertEquals(inputExchanges.stream().map(NftExchange::asGrpc).toList(), exchanges);
    }

    @Test
    void translatesFungibleTransfersAsExpected() {
        final var inputTransfers = wellKnownTransfers();
        final var expectedAdjustments = TokenTransferList.newBuilder()
                .setToken(token)
                .addTransfers(aaWith(anAccount, aChange))
                .addTransfers(aaWith(otherAccount, bChange))
                .addTransfers(aaWith(anotherAccount, cChange))
                .build();
        final var fungibleSubject = new TokenTransferWrapper(Collections.emptyList(), inputTransfers);

        final var builder = fungibleSubject.asGrpcBuilder();
        assertEquals(expectedAdjustments, builder.build());
    }

    @Test
    void translatesNoopAsExpected() {
        final var nothingSubject = new TokenTransferWrapper(Collections.emptyList(), Collections.emptyList());

        final var builder = nothingSubject.asGrpcBuilder();
        assertEquals(TokenTransferList.getDefaultInstance(), builder.build());
    }

    private AccountAmount aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .build();
    }

    private List<NftExchange> wellKnownExchanges() {
        return List.of(
                new NftExchange(aSerialNo, token, anAccount, otherAccount),
                new NftExchange(bSerialNo, token, anAccount, anotherAccount));
    }

    private List<FungibleTokenTransfer> wellKnownTransfers() {
        return List.of(
                new FungibleTokenTransfer(Math.abs(aChange), false, token, anAccount, null),
                new FungibleTokenTransfer(Math.abs(bChange), false, token, null, otherAccount),
                new FungibleTokenTransfer(Math.abs(cChange), false, token, null, anotherAccount));
    }

    private final long aSerialNo = 1_234L;
    private final long bSerialNo = 2_234L;
    private final long aChange = -100L;
    private final long bChange = +75;
    private final long cChange = +25;
    private final AccountID anAccount = new Id(0, 0, 1234).asGrpcAccount();
    private final AccountID otherAccount = new Id(0, 0, 2345).asGrpcAccount();
    private final AccountID anotherAccount = new Id(0, 0, 3456).asGrpcAccount();
}
