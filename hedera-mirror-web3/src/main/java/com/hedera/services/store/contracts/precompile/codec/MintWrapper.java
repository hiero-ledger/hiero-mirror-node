// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;

public record MintWrapper(long amount, TokenID tokenType, List<ByteString> metadata) implements Wrapper {
    private static final long NONFUNGIBLE_MINT_AMOUNT = -1;
    private static final List<ByteString> FUNGIBLE_MINT_METADATA = Collections.emptyList();

    @NonNull
    public static MintWrapper forNonFungible(final TokenID tokenType, final List<ByteString> metadata) {
        return new MintWrapper(NONFUNGIBLE_MINT_AMOUNT, tokenType, metadata);
    }

    @NonNull
    public static MintWrapper forFungible(final TokenID tokenType, final long amount) {
        return new MintWrapper(amount, tokenType, FUNGIBLE_MINT_METADATA);
    }

    public TokenType type() {
        return (amount == NONFUNGIBLE_MINT_AMOUNT) ? NON_FUNGIBLE_UNIQUE : FUNGIBLE_COMMON;
    }
}
