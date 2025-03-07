// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.contracts;

import static com.hedera.services.hapi.utils.contracts.ParsingConstants.burnReturnType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.hapiAllowanceOfType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.intAddressTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.mintReturnType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.hapi.utils.contracts.ParsingConstants.FunctionType;
import org.junit.jupiter.api.Test;

class ParsingConstantsTest {

    @Test
    void functionTypeValidation() {
        assertEquals("ERC_APPROVE", FunctionType.ERC_APPROVE.name());
        assertEquals("ERC_IS_APPROVED_FOR_ALL", FunctionType.ERC_IS_APPROVED_FOR_ALL.name());
        assertEquals("ERC_TRANSFER", FunctionType.ERC_TRANSFER.name());
        assertEquals("HAPI_MINT", FunctionType.HAPI_MINT.name());
        assertEquals("HAPI_BURN", FunctionType.HAPI_BURN.name());
        assertEquals("HAPI_CREATE", FunctionType.HAPI_CREATE.name());
        assertEquals("HAPI_ALLOWANCE", FunctionType.HAPI_ALLOWANCE.name());
        assertEquals("HAPI_APPROVE_NFT", FunctionType.HAPI_APPROVE_NFT.name());
        assertEquals("HAPI_APPROVE", FunctionType.HAPI_APPROVE.name());
        assertEquals("HAPI_GET_APPROVED", FunctionType.HAPI_GET_APPROVED.name());
        assertEquals("HAPI_IS_APPROVED_FOR_ALL", FunctionType.HAPI_IS_APPROVED_FOR_ALL.name());
    }

    @Test
    void tupleTypesValidation() {
        assertEquals(burnReturnType, TupleType.parse("(int32,uint64)"));
        assertEquals(intAddressTuple, TupleType.parse("(int32,address)"));
        assertEquals(mintReturnType, TupleType.parse("(int32,uint64,int64[])"));
        assertEquals(hapiAllowanceOfType, TupleType.parse("(int32,uint256)"));
    }
}
