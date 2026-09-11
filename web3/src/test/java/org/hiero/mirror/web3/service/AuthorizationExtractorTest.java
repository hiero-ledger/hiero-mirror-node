// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.SignatureUtils.EC_DOMAIN_PARAMETERS;

import com.google.common.collect.Range;
import com.hedera.node.app.hapi.utils.ethereum.CodeDelegation;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class AuthorizationExtractorTest extends Web3IntegrationTest {

    private static final SECP256K1 SECP256K1 = new SECP256K1();
    private static final int DEFAULT_MAX_TOUCHED_ACCOUNTS = 1000;

    private final AuthorizationExtractor authorizationExtractor;

    @Resource
    private PrestateProperties prestateProperties;

    @AfterEach
    void tearDown() {
        prestateProperties.setMaxTouchedAccounts(DEFAULT_MAX_TOUCHED_ACCOUNTS);
    }

    @Test
    void extractSignersIgnoresNullAndEmptyAuthorizationList() {
        final var context = prestateContext();

        authorizationExtractor.extractSigners(context, ethereumTransaction(null));
        authorizationExtractor.extractSigners(context, ethereumTransaction(List.of()));

        assertThat(context.getAccounts()).isEmpty();
        assertThat(context.getNonceDeltas()).isEmpty();
        assertThat(context.getPostNonces()).isEmpty();
    }

    @Test
    void extractSignersAppliesAuthorityNonceFromAuthorization() {
        final var authorityId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var keyPair = SECP256K1.generateKeyPair();
        final var authorityAddress = evmAddressFromKeyPair(keyPair);
        persistAccount(authorityId, createdTimestamp, authorityAddress, authorityAddress);
        final var authorization = signedAuthorization(keyPair, domainBuilder.bytes(20), 4L);
        final var context = prestateContext(createdTimestamp + 100);

        authorizationExtractor.extractSigners(context, ethereumTransaction(List.of(authorization)));

        assertThat(context.getAccounts()).containsExactly(authorityId.getId());
        assertThat(context.getNonceDeltas()).containsEntry(authorityId.getId(), 1L);
        assertThat(context.getPostNonces()).containsEntry(authorityId.getId(), 5L);
    }

    @Test
    void extractSignersResolvesAuthorityByAlias() {
        final var authorityId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var keyPair = SECP256K1.generateKeyPair();
        final var authorityAddress = evmAddressFromKeyPair(keyPair);
        persistAccount(authorityId, createdTimestamp, null, authorityAddress);
        final var authorization = signedAuthorization(keyPair, domainBuilder.bytes(20), 7L);
        final var context = prestateContext(createdTimestamp + 100);

        authorizationExtractor.extractSigners(context, ethereumTransaction(List.of(authorization)));

        assertThat(context.getAccounts()).containsExactly(authorityId.getId());
        assertThat(context.getNonceDeltas()).containsEntry(authorityId.getId(), 1L);
        assertThat(context.getPostNonces()).containsEntry(authorityId.getId(), 8L);
    }

    @Test
    void extractSignersAppliesMultipleAuthorities() {
        final var firstAuthorityId = domainBuilder.entityId();
        final var secondAuthorityId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var firstKeyPair = SECP256K1.generateKeyPair();
        final var secondKeyPair = SECP256K1.generateKeyPair();
        final var firstAuthorityAddress = evmAddressFromKeyPair(firstKeyPair);
        persistAccount(firstAuthorityId, createdTimestamp, firstAuthorityAddress, firstAuthorityAddress);
        final var secondAuthorityAddress = evmAddressFromKeyPair(secondKeyPair);
        persistAccount(secondAuthorityId, createdTimestamp, secondAuthorityAddress, secondAuthorityAddress);
        final var context = prestateContext(createdTimestamp + 100);

        authorizationExtractor.extractSigners(
                context,
                ethereumTransaction(List.of(
                        signedAuthorization(firstKeyPair, domainBuilder.bytes(20), 4L),
                        signedAuthorization(secondKeyPair, domainBuilder.bytes(20), 7L))));

        assertThat(context.getNonceDeltas())
                .containsEntry(firstAuthorityId.getId(), 1L)
                .containsEntry(secondAuthorityId.getId(), 1L);
        assertThat(context.getPostNonces())
                .containsEntry(firstAuthorityId.getId(), 5L)
                .containsEntry(secondAuthorityId.getId(), 8L);
    }

    @Test
    void extractSignersSkipsInvalidAndUnresolvedAuthorities() {
        final var authorityId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var knownKeyPair = SECP256K1.generateKeyPair();
        final var unknownKeyPair = SECP256K1.generateKeyPair();
        persistAccount(
                authorityId,
                createdTimestamp,
                evmAddressFromKeyPair(knownKeyPair),
                evmAddressFromKeyPair(knownKeyPair));
        final var invalidAuthorization = Authorization.builder()
                .address("0x" + bytesToHex(domainBuilder.bytes(20)))
                .chainId("0x0")
                .build();
        final var context = prestateContext(createdTimestamp + 100);

        authorizationExtractor.extractSigners(
                context,
                ethereumTransaction(List.of(
                        invalidAuthorization,
                        signedAuthorization(unknownKeyPair, domainBuilder.bytes(20), 1L),
                        signedAuthorization(knownKeyPair, domainBuilder.bytes(20), 4L))));

        assertThat(context.getAccounts()).containsExactly(authorityId.getId());
        assertThat(context.getNonceDeltas()).containsEntry(authorityId.getId(), 1L);
        assertThat(context.getPostNonces()).containsEntry(authorityId.getId(), 5L);
    }

    @Test
    void extractSignersDoesNothingWhenAccountCapAlreadyReached() {
        final var authorityId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var keyPair = SECP256K1.generateKeyPair();
        persistAccount(authorityId, createdTimestamp, evmAddressFromKeyPair(keyPair), evmAddressFromKeyPair(keyPair));
        prestateProperties.setMaxTouchedAccounts(1);
        final var context = prestateContext(createdTimestamp + 100);
        context.addCreatedAccount(domainBuilder.entityId().getId());

        authorizationExtractor.extractSigners(
                context, ethereumTransaction(List.of(signedAuthorization(keyPair, domainBuilder.bytes(20), 4L))));

        assertThat(context.getAccounts()).doesNotContain(authorityId.getId());
        assertThat(context.getNonceDeltas()).isEmpty();
        assertThat(context.getPostNonces()).isEmpty();
    }

    private static PrestateContext prestateContext() {
        return prestateContext(1L);
    }

    private static PrestateContext prestateContext(final long consensusTimestamp) {
        return new PrestateContext(
                new PrestateProperties(),
                consensusTimestamp,
                new PrestateRequest(new TransactionHashParameter(Bytes.repeat((byte) 1, 32)), true, false, false));
    }

    private static EthereumTransaction ethereumTransaction(final List<Authorization> authorizations) {
        return EthereumTransaction.builder().authorizationList(authorizations).build();
    }

    private void persistAccount(
            final EntityId entityId, final long createdTimestamp, final byte[] evmAddress, final byte[] alias) {
        domainBuilder
                .entity(entityId, createdTimestamp)
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .ethereumNonce(99L)
                        .evmAddress(evmAddress)
                        .alias(alias)
                        .deleted(false)
                        .timestampRange(Range.atLeast(createdTimestamp)))
                .persist();
    }

    private static Authorization signedAuthorization(final KeyPair keyPair, final byte[] target, final long nonce) {
        final var unsigned = new CodeDelegation(new byte[] {0}, target, nonce, 0, new byte[] {1}, new byte[] {1});
        final var message = unsigned.calculateSignableMessage();
        final var hash = Bytes32.wrap(new Keccak.Digest256().digest(message));
        final var signature = SECP256K1.sign(hash, keyPair);
        final var hex = HexFormat.of();
        return Authorization.builder()
                .chainId("0x0")
                .address("0x" + hex.formatHex(target))
                .nonce(nonce)
                .yParity(signature.getRecId() == 0 ? "0x0" : "0x1")
                .r("0x" + hex.formatHex(toUnsigned32(signature.getR())))
                .s("0x" + hex.formatHex(toUnsigned32(signature.getS())))
                .build();
    }

    private static byte[] evmAddressFromKeyPair(final KeyPair keyPair) {
        final var compressed =
                keyPair.getPublicKey().asEcPoint(EC_DOMAIN_PARAMETERS).getEncoded(true);
        return SignatureUtils.recoverAddressFromPubKey(compressed);
    }

    private static byte[] toUnsigned32(final BigInteger value) {
        final var hex = value.toString(16);
        final var padded = hex.length() >= 64 ? hex.substring(hex.length() - 64) : "0".repeat(64 - hex.length()) + hex;
        return HexFormat.of().parseHex(padded);
    }
}
