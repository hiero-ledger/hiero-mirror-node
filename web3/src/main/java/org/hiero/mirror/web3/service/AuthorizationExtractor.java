// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.node.app.hapi.utils.ethereum.CodeDelegation;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@RequiredArgsConstructor
@NullMarked
final class AuthorizationExtractor {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final EntityRepository entityRepository;
    private final PrestateProperties prestateProperties;

    void extractSigners(final PrestateContext prestateContext, final EthereumTransaction ethereumTransaction) {
        final var authorizations = ethereumTransaction.getAuthorizationList();
        if (authorizations == null || authorizations.isEmpty()) {
            return;
        }

        final int maxTouchedAccounts = prestateProperties.getMaxTouchedAccounts();
        if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
            return;
        }

        final var recoveredAuthorizations = recoverAuthorizations(authorizations);
        if (recoveredAuthorizations.isEmpty()) {
            return;
        }

        final var recoveredAddresses = new ArrayList<byte[]>(recoveredAuthorizations.size());
        for (final var recovered : recoveredAuthorizations) {
            recoveredAddresses.add(recovered.address());
        }

        final var entities = entityRepository.findActiveByEvmAddressesOrAliasesAndTimestamp(
                recoveredAddresses, prestateContext.getConsensusTimestamp());
        if (entities.isEmpty()) {
            return;
        }

        final var entityByAddress = toEntityByAddress(entities);
        for (final var recoveredAuthorization : recoveredAuthorizations) {
            if (prestateContext.getAccounts().size() >= maxTouchedAccounts) {
                break;
            }
            final var entity = entityByAddress.get(Bytes.wrap(recoveredAuthorization.address()));
            if (entity == null) {
                continue;
            }
            final long authorityId = entity.getId();
            prestateContext.addAccount(entity.toEntityId());
            prestateContext.addNonceDelta(authorityId, 1L);
            prestateContext.putPostNonce(authorityId, recoveredAuthorization.nonce() + 1L);
        }
    }

    private List<RecoveredAuthorization> recoverAuthorizations(final List<Authorization> authorizations) {
        final var recovered = new ArrayList<RecoveredAuthorization>(authorizations.size());
        for (final var authorization : authorizations) {
            final var recoveredAuthorization = recoverAuthorization(authorization);
            if (recoveredAuthorization != null) {
                recovered.add(recoveredAuthorization);
            }
        }
        return recovered;
    }

    private @Nullable RecoveredAuthorization recoverAuthorization(final Authorization authorization) {
        final var nonce = authorization.getNonce();
        if (nonce == null) {
            return null;
        }

        final var codeDelegation = toCodeDelegation(authorization);
        if (codeDelegation == null) {
            return null;
        }

        final var recovered = EthTxSigs.extractAuthoritySignature(codeDelegation);
        if (recovered.isEmpty()) {
            return null;
        }

        final var address = recovered.get().address();
        if (address == null || address.length != EVM_ADDRESS_LENGTH) {
            return null;
        }
        return new RecoveredAuthorization(address, nonce);
    }

    private record RecoveredAuthorization(byte[] address, long nonce) {}

    private Map<Bytes, Entity> toEntityByAddress(final List<Entity> entities) {
        final var entityByAddress = HashMap.<Bytes, Entity>newHashMap(entities.size() * 2);
        for (final var entity : entities) {
            final var evmAddress = entity.getEvmAddress();
            if (evmAddress != null) {
                entityByAddress.put(Bytes.wrap(evmAddress), entity);
            }
            final var alias = entity.getAlias();
            if (alias != null) {
                entityByAddress.put(Bytes.wrap(alias), entity);
            }
        }
        return entityByAddress;
    }

    private static @Nullable CodeDelegation toCodeDelegation(final Authorization authorization) {
        if (authorization.getNonce() == null) {
            return null;
        }
        try {
            return new CodeDelegation(
                    parseHex(authorization.getChainId()),
                    parseHex(authorization.getAddress()),
                    authorization.getNonce(),
                    parseYParity(authorization.getYParity()),
                    parseHex(authorization.getR()),
                    parseHex(authorization.getS()));
        } catch (final IllegalArgumentException _) {
            return null;
        }
    }

    private static byte[] parseHex(final @Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        var stripped = hex;
        if (stripped.startsWith(HEX_PREFIX) || stripped.startsWith("0X")) {
            stripped = stripped.substring(HEX_PREFIX.length());
        }
        if (stripped.isEmpty()) {
            return new byte[0];
        }
        if ((stripped.length() & 1) == 1) {
            stripped = "0" + stripped;
        }
        return HEX_FORMAT.parseHex(stripped);
    }

    private static int parseYParity(final @Nullable String yParity) {
        final var bytes = parseHex(yParity);
        if (bytes.length == 0) {
            return 0;
        }
        return bytes[bytes.length - 1] & 0x01;
    }
}
