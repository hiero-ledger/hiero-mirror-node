// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.hapi.utils.contracts.ParsingConstants;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;

/**
 * Solidity types used by ABI methods defined in `SystemContractAbis`
 * <p>
 * Must be broken out into a separate class to avoid forward references if they're put where
 * they're more appropriate: Right into the enum declaration `SystemContractAbis`.
 */
public final class SystemContractTypes {

    public static final String ACCOUNT_AMOUNT_V1 = "(address,int64)";
    public static final String ACCOUNT_AMOUNT_V2 = "(address,int64,bool)";

    public static final String EXPIRY_V1 = ParsingConstants.EXPIRY;
    public static final String EXPIRY_V2 = ParsingConstants.EXPIRY_V2;

    public static final String FIXED_FEE_V1 = ParsingConstants.FIXED_FEE;
    public static final String FIXED_FEE_V2 = ParsingConstants.FIXED_FEE_V2;

    public static final String FRACTIONAL_FEE_V1 = ParsingConstants.FRACTIONAL_FEE;
    public static final String FRACTIONAL_FEE_V2 = ParsingConstants.FRACTIONAL_FEE_V2;

    public static final String HEDERA_TOKEN_STRUCT_V1 = DecodingFacade.HEDERA_TOKEN_STRUCT;
    public static final String HEDERA_TOKEN_STRUCT_V2 = DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
    public static final String HEDERA_TOKEN_STRUCT_V3 = DecodingFacade.HEDERA_TOKEN_STRUCT_V3;

    public static final String NFT_TRANSFER_V1 = "(address,address,int64)";
    public static final String NFT_TRANSFER_V2 = "(address,address,int64,bool)";

    public static final String ROYALTY_FEE_V1 = ParsingConstants.ROYALTY_FEE;
    public static final String ROYALTY_FEE_V2 = ParsingConstants.ROYALTY_FEE_V2;

    public static final String TOKEN_TRANSFER_LIST_V1 =
            "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_V1, NFT_TRANSFER_V1);
    public static final String TOKEN_TRANSFER_LIST_V2 =
            "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_V2, NFT_TRANSFER_V2);

    public static final String TRANSFER_LIST_V1 = "(%s[])".formatted(ACCOUNT_AMOUNT_V2);

    private SystemContractTypes() {}
}
