// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.springframework.util.CollectionUtils;

public abstract class AbstractOpcodeTracer {

    protected final List<String> captureMemory(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isMemory()) {
            return Collections.emptyList();
        }

        int size = frame.memoryWordSize();
        var memory = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            memory.add(frame.readMemory(i * 32L, 32).toHexString());
        }

        return memory;
    }

    protected final List<String> captureStack(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStack()) {
            return Collections.emptyList();
        }

        int size = frame.stackSize();
        var stack = new ArrayList<String>(size);
        for (int i = 0; i < size; ++i) {
            stack.add(frame.getStackItem(size - 1 - i).toHexString());
        }

        return stack;
    }

    protected Map<String, String> captureStorage(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
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
            final var updates = rootProxyWorldUpdater
                    .getEvmFrameState()
                    .getTxStorageUsage(true)
                    .accesses();
            Map<String, String> result = new TreeMap<>();
            for (var storageAccesses : updates) {
                for (var access : storageAccesses.accesses()) {
                    var key = access.key().toHexString();
                    if (!result.containsKey(key)) {
                        var value = access.writtenValue() != null
                                ? access.writtenValue().toHexString()
                                : access.value().toHexString();
                        result.put(key, value);
                    }
                }
            }
            return result;

        } catch (final ModificationNotAllowedException e) {
            return Collections.emptyMap();
        }
    }

    protected final Bytes getRevertReasonFromContractActions(final ContractCallContext context) {
        final var contractActions = context.getContractActions();

        if (CollectionUtils.isEmpty(contractActions)) {
            return null;
        }

        for (var action : contractActions) {
            if (action.hasRevertReason()) {
                return formatRevertReason(Bytes.of(action.getResultData()));
            }
        }
        return null;
    }

    /**
     * Formats the revert reason to be consistent with the revert reason format in the EVM. <a
     * href="https://besu.hyperledger.org/23.10.2/private-networks/how-to/send-transactions/revert-reason#revert-reason-format">...</a>
     *
     * @param revertReason the revert reason
     * @return the formatted revert reason
     */
    protected final Bytes formatRevertReason(final Bytes revertReason) {
        if (revertReason == null || revertReason.isZero()) {
            return Bytes.EMPTY;
        }

        // covers an edge case where the reason in the contract actions is a response code number (as a plain string)
        // so we convert this number to an ABI-encoded string of the corresponding response code name,
        // to at least give some relevant information to the user in the valid EVM format
        final var trimmedReason = revertReason.trimLeadingZeros();
        if (trimmedReason.size() <= Integer.BYTES) {
            final var responseCode = ResponseCodeEnum.forNumber(trimmedReason.toInt());
            if (responseCode != null) {
                String hexResult = BytesDecoder.getAbiEncodedRevertReason(responseCode.name());
                return Bytes.fromHexString(hexResult);
            }
        }

        String hexResult = BytesDecoder.getAbiEncodedRevertReason(revertReason.toArray());
        return Bytes.fromHexString(hexResult);
    }
}
