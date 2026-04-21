// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.convert.BytesDecoder.startsWithErrorSelector;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.springframework.util.CollectionUtils;

public abstract class AbstractOpcodeTracer {

    protected final List<Bytes> captureMemory(final MessageFrame frame, final OpcodeContext options) {
        if (!options.isMemory()) {
            return Collections.emptyList();
        }
        var size = frame.memoryWordSize();
        var memoryWords = new ArrayList<Bytes>(size);

        final var wordSize = 32;
        final var memory = frame.readMutableMemory(0L, (long) wordSize * size).toArrayUnsafe();

        for (var i = 0; i < size; i++) {
            var startIndex = i * wordSize;
            memoryWords.add(Bytes.wrap(Arrays.copyOfRange(memory, startIndex, startIndex + wordSize)));
        }

        return memoryWords;
    }

    protected final List<Bytes> captureStack(final MessageFrame frame, final OpcodeContext options) {
        if (!options.isStack()) {
            return Collections.emptyList();
        }

        var size = frame.stackSize();
        var stack = new ArrayList<Bytes>(size);
        for (var i = 0; i < size; ++i) {
            var item = frame.getStackItem(size - 1 - i);
            stack.add(Bytes.wrap(item.toArrayUnsafe()));
        }

        return stack;
    }

    protected Map<Bytes, Bytes> captureStorage(
            final MessageFrame frame, final OpcodeContext options, final ContractCallContext context) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
            if (context.getOpcodeContext().getRootProxyWorldUpdater() == null) {

                var worldUpdater = frame.getWorldUpdater();
                var parent = worldUpdater.parentUpdater().orElse(null);
                while (parent != null) {
                    worldUpdater = parent;
                    parent = worldUpdater.parentUpdater().orElse(null);
                }

                if (!(worldUpdater instanceof RootProxyWorldUpdater rootProxyWorldUpdater)) {
                    // The storage updates are kept only in the RootProxyWorldUpdater.
                    // If we don't have one -> something unexpected happened and an attempt to
                    // get the storage changes from a ProxyWorldUpdater would result in a
                    // NullPointerException, so in this case just return an empty map.
                    return Collections.emptyMap();
                }

                context.getOpcodeContext().setRootProxyWorldUpdater(rootProxyWorldUpdater);
            }

            final var rootProxyWorldUpdater = context.getOpcodeContext().getRootProxyWorldUpdater();
            final var updates = rootProxyWorldUpdater
                    .getEvmFrameState()
                    .getTxStorageUsage(true)
                    .accesses();

            if (updates.isEmpty()) {
                return Collections.emptyMap();
            }

            final var result = new TreeMap<Bytes, Bytes>();
            for (final var storageAccesses : updates) {
                for (final var access : storageAccesses.accesses()) {
                    final var key = access.key();
                    if (!result.containsKey(key)) {
                        final var value = access.writtenValue() != null ? access.writtenValue() : access.value();
                        result.put(key, value);
                    }
                }
            }
            return result;

        } catch (final ModificationNotAllowedException e) {
            return Collections.emptyMap();
        }
    }

    protected final String getRevertReasonFromContractActions(final ContractCallContext context) {
        final var contractActions = context.getOpcodeContext().getActions();

        if (CollectionUtils.isEmpty(contractActions)) {
            return null;
        }

        for (var action : contractActions) {
            if (action.hasRevertReason()) {
                return formatRevertReason(action.getResultData());
            }
        }
        return null;
    }

    /**
     * Formats the revert reason to be consistent with the revert reason format in the EVM. <a
     * href="https://besu.hyperledger.org/23.10.2/private-networks/how-to/send-transactions/revert-reason#revert-reason-format">...</a>
     *
     * @param revertReason the revert reason as byte array
     * @return the formatted revert reason as hex string
     */
    protected final String formatRevertReason(final byte[] revertReason) {
        if (revertReason == null || revertReason.length == 0 || isZero(revertReason)) {
            return HEX_PREFIX;
        }

        if (startsWithErrorSelector(revertReason)) {
            return HEX_PREFIX + Hex.toHexString(revertReason);
        }

        final var firstNonZero = findFirstNonZero(revertReason);
        final var trimmedLength = revertReason.length - firstNonZero;
        if (trimmedLength <= Integer.BYTES) {
            final var responseCode = ResponseCodeEnum.forNumber(toInt(revertReason, firstNonZero));
            if (responseCode != null) {
                return BytesDecoder.getAbiEncodedRevertReason(responseCode.name());
            }
        }

        return BytesDecoder.getAbiEncodedRevertReason(new String(revertReason, StandardCharsets.UTF_8));
    }

    private boolean isZero(final byte[] bytes) {
        for (var b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private int findFirstNonZero(final byte[] bytes) {
        var index = 0;
        while (index < bytes.length && bytes[index] == 0) {
            index++;
        }
        return index;
    }

    private int toInt(final byte[] bytes, final int offset) {
        var result = 0;
        for (var i = offset; i < bytes.length; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }
}
