// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.node.app.service.contract.impl.exec.scope.DefaultVerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.spi.workflows.HandleContext;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;

/**
 * Verification strategies for standalone contract simulation that enforce active contract key checks
 * ({@code CONTRACT_ID} and {@code DELEGATABLE_CONTRACT_ID}) like the consensus node while treating cryptographic
 * keys as valid. This ensures that when a contract (such as a DEX router) calls HTS {@code transferToken} to debit
 * another contract whose key is its own {@code CONTRACT_ID}, {@code ApprovalSwitchHelper} recognizes that the
 * caller does not hold an active signature for that contract and switches the debit to an allowance spend
 * ({@code isApproval = true}).
 */
final class SimulationVerificationStrategies implements VerificationStrategies {

    private static final VerificationStrategies DEFAULT_VERIFICATION_STRATEGIES = new DefaultVerificationStrategies();

    @Override
    public VerificationStrategy activatingOnlyContractKeysFor(
            final Address sender,
            final boolean requiresDelegatePermission,
            final HederaNativeOperations nativeOperations) {
        final var activeContractStrategy = DEFAULT_VERIFICATION_STRATEGIES.activatingOnlyContractKeysFor(
                sender, requiresDelegatePermission, nativeOperations);
        return new VerificationStrategy() {
            @Override
            public Decision decideForPrimitive(final Key key) {
                return switch (key.key().kind()) {
                    case CONTRACT_ID, DELEGATABLE_CONTRACT_ID -> activeContractStrategy.decideForPrimitive(key);
                    default -> Decision.VALID;
                };
            }

            @Override
            public Predicate<Key> asSignatureTestIn(final HandleContext context, final Key maybeEthSenderKey) {
                final var primitiveTest = asPrimitiveSignatureTestIn(context, maybeEthSenderKey);
                return key -> matchesKey(key, primitiveTest);
            }
        };
    }

    private static boolean matchesKey(final Key key, final Predicate<Key> primitiveTest) {
        return switch (key.key().kind()) {
            case KEY_LIST -> {
                final var keys = key.keyListOrThrow().keys();
                if (keys.isEmpty()) {
                    yield false;
                }
                for (final var childKey : keys) {
                    if (!matchesKey(childKey, primitiveTest)) {
                        yield false;
                    }
                }
                yield true;
            }
            case THRESHOLD_KEY -> {
                final var thresholdKey = key.thresholdKeyOrThrow();
                final var keys = thresholdKey.keysOrElse(KeyList.DEFAULT).keys();
                final var clampedThreshold = Math.max(1, Math.min(thresholdKey.threshold(), keys.size()));
                var passed = 0;
                for (final var childKey : keys) {
                    if (matchesKey(childKey, primitiveTest)) {
                        passed++;
                    }
                }
                yield passed >= clampedThreshold;
            }
            default -> primitiveTest.test(key);
        };
    }
}
