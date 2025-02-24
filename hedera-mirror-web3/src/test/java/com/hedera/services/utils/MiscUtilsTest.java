// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hedera.services.utils.MiscUtils.perm64;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class MiscUtilsTest {

    @Test
    void asFcKeyUncheckedTranslatesExceptions() {
        final var key = Key.getDefaultInstance();
        assertThrows(IllegalArgumentException.class, () -> MiscUtils.asFcKeyUnchecked(key));
    }

    @Test
    void asFcKeyReturnsEmptyOnUnparseableKey() {
        final var key = Key.getDefaultInstance();
        assertTrue(asUsableFcKey(key).isEmpty());
    }

    @Test
    void asFcKeyReturnsEmptyOnEmptyKey() {
        assertTrue(asUsableFcKey(Key.newBuilder()
                        .setKeyList(KeyList.getDefaultInstance())
                        .build())
                .isEmpty());
    }

    @Test
    void asFcKeyReturnsEmptyOnInvalidKey() {
        assertTrue(asUsableFcKey(Key.newBuilder()
                        .setEd25519(ByteString.copyFrom("1".getBytes()))
                        .build())
                .isEmpty());
    }

    @Test
    void perm64Test() {
        assertEquals(0L, perm64(0L));
        assertEquals(-4328535976359616544L, perm64(1L));
        assertEquals(2657016865369639288L, perm64(7L));
    }

    @Test
    void testAsPrimitiveKeyUncheckedFails() {
        final var ecdsaKeyBytes = unhex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
        final var alias = ByteString.copyFrom(ecdsaKeyBytes);

        assertThrows(IllegalStateException.class, () -> MiscUtils.asPrimitiveKeyUnchecked(alias));
    }

    @Test
    void canGetSynthAccessor() {
        final var synth = MiscUtils.synthAccessorFor(
                TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance()));

        assertEquals(CryptoTransfer, synth.getFunction());
    }

    @Test
    void testAsPrimitiveKeyUnchecked() {
        final var ecdsaKeyBytes = unhex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
        final var ecdsaKey = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(ecdsaKeyBytes))
                .build();

        assertEquals(ecdsaKey, MiscUtils.asPrimitiveKeyUnchecked(ecdsaKey.toByteString()));
    }

    @Test
    void getsExpectedTxnFunctionality() {
        final Map<HederaFunctionality, BodySetter<? extends GeneratedMessage, Builder>> setters =
                new EnumMap<>(HederaFunctionality.class) {
                    {
                        put(ContractCall, new BodySetter<>(ContractCallTransactionBody.class));
                        put(ContractCreate, new BodySetter<>(ContractCreateTransactionBody.class));
                        put(EthereumTransaction, new BodySetter<>(EthereumTransactionBody.class));
                        put(ContractUpdate, new BodySetter<>(ContractUpdateTransactionBody.class));
                        put(CryptoCreate, new BodySetter<>(CryptoCreateTransactionBody.class));
                        put(CryptoDelete, new BodySetter<>(CryptoDeleteTransactionBody.class));
                        put(CryptoTransfer, new BodySetter<>(CryptoTransferTransactionBody.class));
                        put(CryptoUpdate, new BodySetter<>(CryptoUpdateTransactionBody.class));
                        put(ContractDelete, new BodySetter<>(ContractDeleteTransactionBody.class));
                        put(TokenCreate, new BodySetter<>(TokenCreateTransactionBody.class));
                        put(TokenFreezeAccount, new BodySetter<>(TokenFreezeAccountTransactionBody.class));
                        put(TokenUnfreezeAccount, new BodySetter<>(TokenUnfreezeAccountTransactionBody.class));
                        put(TokenGrantKycToAccount, new BodySetter<>(TokenGrantKycTransactionBody.class));
                        put(TokenRevokeKycFromAccount, new BodySetter<>(TokenRevokeKycTransactionBody.class));
                        put(TokenDelete, new BodySetter<>(TokenDeleteTransactionBody.class));
                        put(TokenUpdate, new BodySetter<>(TokenUpdateTransactionBody.class));
                        put(TokenMint, new BodySetter<>(TokenMintTransactionBody.class));
                        put(TokenBurn, new BodySetter<>(TokenBurnTransactionBody.class));
                        put(TokenAccountWipe, new BodySetter<>(TokenWipeAccountTransactionBody.class));
                        put(TokenAssociateToAccount, new BodySetter<>(TokenAssociateTransactionBody.class));
                        put(TokenDissociateFromAccount, new BodySetter<>(TokenDissociateTransactionBody.class));
                        put(TokenUnpause, new BodySetter<>(TokenUnpauseTransactionBody.class));
                        put(TokenPause, new BodySetter<>(TokenPauseTransactionBody.class));
                        put(Freeze, new BodySetter<>(FreezeTransactionBody.class));
                        put(CryptoApproveAllowance, new BodySetter<>(CryptoApproveAllowanceTransactionBody.class));
                    }
                };

        setters.forEach((function, setter) -> {
            final var txn = TransactionBody.newBuilder();
            setter.setDefaultInstanceFor(txn);
            try {
                final var input = txn.build();
                assertEquals(function, functionOf(input));
            } catch (final Exception uhf) {
                Assertions.fail("Failed HederaFunctionality check :: " + uhf.getMessage());
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class BodySetter<T, B> {
        private final Class<T> type;

        public BodySetter(final Class<T> type) {
            this.type = type;
        }

        void setDefaultInstanceFor(final B builder) {
            try {
                final var setter = getSetter(builder, type);
                final var defaultGetter = type.getDeclaredMethod("getDefaultInstance");
                final T defaultInstance = (T) defaultGetter.invoke(null);
                setter.invoke(builder, defaultInstance);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }

        void setActiveHeaderFor(final B builder) {
            try {
                final var newBuilderMethod = type.getDeclaredMethod("newBuilder");
                final var opBuilder = newBuilderMethod.invoke(null);
                final var opBuilderClass = opBuilder.getClass();
                final var setHeaderMethod = opBuilderClass.getDeclaredMethod("setHeader", QueryHeader.Builder.class);
                setHeaderMethod.invoke(opBuilder, QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
                final var setter = getSetter(builder, opBuilderClass);
                setter.invoke(builder, opBuilder);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private Method getSetter(final B builder, final Class type) {
            return Stream.of(builder.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("set") && m.getParameterTypes()[0].equals(type))
                    .findFirst()
                    .get();
        }
    }
}
