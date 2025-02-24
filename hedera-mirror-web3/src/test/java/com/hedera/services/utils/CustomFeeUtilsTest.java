// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.txn.token.CreateLogic.FeeType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class CustomFeeUtilsTest {

    private final Address feeAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");

    @Test
    void throwIfConstructorCalled() throws NoSuchMethodException {
        Constructor<CustomFeeUtils> ctor;
        ctor = CustomFeeUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        assertThatThrownBy(ctor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class)
                .hasRootCauseMessage("Utility Class");
    }

    @Test
    void getFeeCollector() {
        var customFee = new CustomFee();
        var fixedFee = new FixedFee(0, null, true, true, feeAddress);
        customFee.setFixedFee(fixedFee);
        var collector = CustomFeeUtils.getFeeCollector(customFee);
        assertEquals(feeAddress, collector);

        customFee = new CustomFee();
        var royaltyFee = new RoyaltyFee(1, 1, 0, null, true, feeAddress);
        customFee.setRoyaltyFee(royaltyFee);
        collector = CustomFeeUtils.getFeeCollector(customFee);
        assertEquals(feeAddress, collector);

        customFee = new CustomFee();
        var fractureFee = new FractionalFee(1, 1, 1, 2, true, feeAddress);
        customFee.setFractionalFee(fractureFee);
        collector = CustomFeeUtils.getFeeCollector(customFee);
        assertEquals(feeAddress, collector);
    }

    @Test
    void getFeeType() {
        var customFee = new CustomFee();
        customFee.setFixedFee(mock(FixedFee.class));
        var feeType = CustomFeeUtils.getFeeType(customFee);
        assertEquals(FeeType.FIXED_FEE, feeType);

        customFee = new CustomFee();
        customFee.setRoyaltyFee(mock(RoyaltyFee.class));
        feeType = CustomFeeUtils.getFeeType(customFee);
        assertEquals(FeeType.ROYALTY_FEE, feeType);

        customFee = new CustomFee();
        customFee.setFractionalFee(mock(FractionalFee.class));
        feeType = CustomFeeUtils.getFeeType(customFee);
        assertEquals(FeeType.FRACTIONAL_FEE, feeType);
    }
}
