// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.txn.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenOpsValidatorTest {

    @Mock
    private OptionValidator validator;

    @Test
    void validateNftTokenMintHappyPath() {
        given(validator.maxBatchSizeMintCheck(1)).willReturn(OK);
        given(validator.nftMetadataCheck(any())).willReturn(OK);

        assertEquals(OK, forMintWith(1, 0, true));
    }

    @Test
    void validateFungibleTokenMintHappyPath() {
        assertEquals(OK, forMintWith(0, 1, false));
    }

    @Test
    void tokenMintFailsWithInvalidTokenCounts() {
        assertEquals(OK, forMintWith(0, 0, true));

        assertEquals(INVALID_TRANSACTION_BODY, forMintWith(1, 1, true));
        assertEquals(INVALID_TOKEN_MINT_AMOUNT, forMintWith(1, -1, true));
        assertEquals(INVALID_TOKEN_MINT_AMOUNT, forMintWith(0, -1, true));
    }

    @Test
    void nftTokenMintFailsWhenNftsDisabled() {
        assertEquals(NOT_SUPPORTED, forMintWith(1, 0, false));
    }

    @Test
    void nftTokenMintFailsWhenInvalidMetaData() {
        given(validator.maxBatchSizeMintCheck(1)).willReturn(OK);
        given(validator.nftMetadataCheck(any())).willReturn(METADATA_TOO_LONG);

        assertEquals(METADATA_TOO_LONG, forMintWith(1, 0, true));
    }

    @Test
    void nftTokenMintFailsWhenInvalidBatchSize() {
        given(validator.maxBatchSizeMintCheck(1)).willReturn(BATCH_SIZE_LIMIT_EXCEEDED);

        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, forMintWith(1, 0, true));
    }

    private ResponseCodeEnum forMintWith(int nftCount, long fungibleCount, boolean areNftEnabled) {
        return validateTokenOpsWith(
                nftCount,
                fungibleCount,
                areNftEnabled,
                INVALID_TOKEN_MINT_AMOUNT,
                new ArrayList<>(List.of(ByteString.copyFromUtf8("memo"))),
                validator::maxBatchSizeMintCheck,
                validator::nftMetadataCheck);
    }
}
