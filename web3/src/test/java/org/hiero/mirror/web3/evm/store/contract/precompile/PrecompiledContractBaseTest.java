// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract.precompile;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import java.util.Deque;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
public abstract class PrecompiledContractBaseTest {

    // mock precompile signature
    static final Bytes MOCK_PRECOMPILE_FUNCTION_HASH = Bytes.fromHexString("0x00000000");
    static final Pair<Long, Bytes> FAILURE_RESULT = Pair.of(0L, null);

    @Mock
    EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    MessageFrame messageFrame;

    @Mock
    MessageFrame parentMessageFrame;

    @Mock
    BlockValues blockValues;

    @Mock
    ViewGasCalculator gasCalculator;

    @Mock
    TokenAccessor tokenAccessor;

    @Mock
    ViewExecutor viewExecutor;

    @Mock
    RedirectViewExecutor redirectViewExecutor;

    @Mock
    HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    HederaEvmWorldStateTokenAccount account;

    @Mock
    PrecompilePricingUtils precompilePricingUtils;

    Deque<MessageFrame> messageFrameStack;
    Store store;

    @InjectMocks
    MockPrecompile mockPrecompile;

    @InjectMocks
    MirrorNodeEvmProperties mirrorNodeEvmProperties;

    Bytes prerequisitesForRedirect(final int descriptor, final Address tokenAddress) {
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                tokenAddress,
                Bytes.of(Integers.toBytes(descriptor)));
    }

    static class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
        @NonNull
        @Override
        public Optional<V> get(@NonNull final K key, final Optional<Long> timestamp) {
            throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
        }
    }
}
