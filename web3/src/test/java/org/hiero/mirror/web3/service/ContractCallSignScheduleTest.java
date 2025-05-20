// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import com.hederahashgraph.api.proto.java.SignaturePair.SignatureCase;
import java.nio.ByteBuffer;
import java.security.KeyPairGenerator;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HRC755Contract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
class ContractCallSignScheduleTest extends AbstractContractCallServiceTest {

    @Test
    void signScheduleWithEcdsaKey() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();
        final var messageHash = getMessageHash(scheduleEntity);

        final var keyPair = Keys.createEcKeyPair();
        final var signatureMapBytes = getSignatureMapBytesEcdsa(messageHash, keyPair);
        final var ecdsaKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ecdsaKey);

        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        final var payerAccount = persistEd25519Account();
        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);
        // When
        final var functionCall = contract.send_signScheduleCall(
                (getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))), signatureMapBytes);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void authorizeScheduleWithContract() {
        // Given
        final var contract = testWeb3jService.deployWithoutPersist(HRC755Contract::deploy);
        final var scheduleContract = scheduleContractPersist(
                testWeb3jService.getContractRuntime(), Address.fromHexString(contract.getContractAddress()));
        final var scheduleEntity = persistScheduleEntity();
        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(scheduleContract, receiverAccount);

        final var payerAccount = persistEd25519Account();
        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);
        // When
        final var functionCall =
                contract.send_authorizeScheduleCall((getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void authorizeScheduleWithContractNoExec() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();
        final var keyPair = Keys.createEcKeyPair();
        final var ecdsaKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ecdsaKey);
        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        final var payerAccount = persistEd25519Account();
        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);
        // When
        final var functionCall =
                contract.send_authorizeScheduleCall((getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void signScheduleWithEcdsaKeyWhichAlreadySignedFails() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();
        final var messageHash = getMessageHash(scheduleEntity);

        final var keyPair = Keys.createEcKeyPair();
        final var signatureMapBytes = getSignatureMapBytesEcdsa(messageHash, keyPair);

        final var ecdsaKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ecdsaKey);

        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        final var payerAccount = persistEd25519Account();
        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);

        // Persist signature
        domainBuilder
                .transactionSignature()
                .customize(ts -> ts.publicKeyPrefix(convertToCompressedPublicKey(keyPair.getPublicKey())
                                .toByteArray())
                        .signature(signMessageECDSA(messageHash, Numeric.toBytesPadded(keyPair.getPrivateKey(), 32)))
                        .entityId(EntityId.of(schedule.getScheduleId()))
                        .type(SignatureCase.ECDSA_SECP256K1.getNumber()))
                .persist();

        // Sign again with the same key
        // When
        final var functionCall = contract.send_signScheduleCall(
                (getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))), signatureMapBytes);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void signScheduleWithEcdsaKeyAndAlreadyExistingPayerEdSignature() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();
        final var messageHash = getMessageHash(scheduleEntity);

        final var keyPair = Keys.createEcKeyPair();
        final var signatureMapBytes = getSignatureMapBytesEcdsa(messageHash, keyPair);

        final var ecdsaKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ecdsaKey);

        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        // Persist payer with custom ED key and get message signature
        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPairEd = keyPairGenerator.generateKeyPair();
        var publicKey = keyPairEd.getPublic();
        var publicKeyCompressed = convertToCompressedPublicKey(publicKey);
        var privateKey = keyPairEd.getPrivate();
        final var signedBytes = signBytesED25519(getMessageBytes(scheduleEntity), privateKey);

        final var ed25519Key =
                Key.newBuilder().setEd25519(publicKeyCompressed).build().toByteArray();
        final var payerAccount = persistAccountWithEvmAddressAndPublicKey(null, ed25519Key);

        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);

        // Persist payer ed signature
        domainBuilder
                .transactionSignature()
                .customize(ts -> ts.publicKeyPrefix(publicKeyCompressed.toByteArray())
                        .signature(signedBytes)
                        .entityId(EntityId.of(schedule.getScheduleId()))
                        .type(SignatureCase.ED25519.getNumber()))
                .persist();

        // When
        final var functionCall = contract.send_signScheduleCall(
                (getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))), signatureMapBytes);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void signScheduleWithEdKeyFailsWhenEcdsaExpected() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();

        // Persist expected ecdsa key signer
        final var keyPair = Keys.createEcKeyPair();
        final var ecdsaKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ecdsaKey);

        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        final var payerAccount = persistEd25519Account();

        // Create unexpected ed25519 key signature
        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPairEd = keyPairGenerator.generateKeyPair();
        var publicKey = keyPairEd.getPublic();
        var publicKeyCompressed = convertToCompressedPublicKey(publicKey);
        var privateKey = keyPairEd.getPrivate();
        final var signedBytes = signBytesED25519(getMessageBytes(scheduleEntity), privateKey);

        final var signatureMap = SignatureMap.newBuilder()
                .sigPair(SignaturePair.newBuilder()
                        .ed25519(Bytes.wrap(signedBytes))
                        .pubKeyPrefix(Bytes.wrap(publicKeyCompressed.toByteArray()))
                        .build())
                .build();

        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);

        // When
        final var functionCall = contract.send_signScheduleCall(
                (getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))),
                SignatureMap.PROTOBUF.toBytes(signatureMap).toByteArray());
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void signScheduleWithEd25519Key() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();

        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPairEd = keyPairGenerator.generateKeyPair();
        var publicKey = keyPairEd.getPublic();
        var publicKeyCompressed = convertToCompressedPublicKey(publicKey);
        var privateKey = keyPairEd.getPrivate();
        final var signedBytes = signBytesED25519(getMessageBytes(scheduleEntity), privateKey);

        final var signatureMap = SignatureMap.newBuilder()
                .sigPair(SignaturePair.newBuilder()
                        .ed25519(Bytes.wrap(signedBytes))
                        .pubKeyPrefix(Bytes.wrap(publicKeyCompressed.toByteArray()))
                        .build())
                .build();

        final var ed25519Key =
                Key.newBuilder().setEd25519(publicKeyCompressed).build().toByteArray();
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ed25519Key);

        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        final var payerAccount = persistEd25519Account();
        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);

        // When
        final var functionCall = contract.send_signScheduleCall(
                (getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))),
                SignatureMap.PROTOBUF.toBytes(signatureMap).toByteArray());
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void signScheduleWithEcdsaKeyFailsWhenEd25519Expected() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var scheduleEntity = persistScheduleEntity();

        // Persist expected ed25519 signer
        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPairEd = keyPairGenerator.generateKeyPair();
        var publicKey = keyPairEd.getPublic();
        var publicKeyCompressed = convertToCompressedPublicKey(publicKey);
        final var ed25519Key =
                Key.newBuilder().setEd25519(publicKeyCompressed).build().toByteArray();
        final var signerAccount = persistAccountWithEvmAddressAndPublicKey(null, ed25519Key);

        final var receiverAccount = accountEntityPersist();
        final var scheduleTransactionBodyBytes =
                buildDefaultScheduleTransactionBodyForCryptoTransferBytes(signerAccount, receiverAccount);

        // Create unexpected ecdsa key signature
        final var keyPair = Keys.createEcKeyPair();
        final var signatureMapBytes = getSignatureMapBytesEcdsa(getMessageHash(scheduleEntity), keyPair);

        final var payerAccount = persistEd25519Account();
        final var schedule = persistSchedule(scheduleEntity, payerAccount, scheduleTransactionBodyBytes);

        // When
        final var functionCall = contract.send_signScheduleCall(
                (getAddressFromEntityId(EntityId.of(schedule.getScheduleId()))), signatureMapBytes);
        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    private Entity persistEd25519Account() {
        return accountEntityPersistCustomizable(e -> e.type(EntityType.ACCOUNT)
                .evmAddress(null)
                .alias(null)
                .balance(DEFAULT_ACCOUNT_BALANCE)
                .key(domainBuilder.key(KeyCase.ED25519)));
    }

    private Entity persistScheduleEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.SCHEDULE))
                .persist();
    }

    private Schedule persistSchedule(
            final Entity scheduleEntity, final Entity payerAccount, final byte[] transactionBody) {
        return domainBuilder
                .schedule()
                .customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                        .transactionBody(transactionBody)
                        .payerAccountId(payerAccount.toEntityId())
                        .creatorAccountId(payerAccount.toEntityId()))
                .persist();
    }

    private byte[] getMessageBytes(final Entity entity) {
        final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 3);
        buffer.putLong(entity.getShard());
        buffer.putLong(entity.getRealm());
        buffer.putLong(entity.getNum());
        return Bytes.wrap(buffer.array()).toByteArray();
    }

    private byte[] getMessageHash(final Entity scheduleEntity) {
        return new Keccak.Digest256().digest(getMessageBytes(scheduleEntity));
    }

    private byte[] getSignatureMapBytesEcdsa(final byte[] messageHash, final ECKeyPair keyPair) {
        final var signedMessage = signMessageECDSA(messageHash, Numeric.toBytesPadded(keyPair.getPrivateKey(), 32));
        var publicKeyBytestring = convertToCompressedPublicKey(keyPair.getPublicKey());
        var compressedPublicKey = publicKeyBytestring.toByteArray();

        final var signatureMap = SignatureMap.newBuilder()
                .sigPair(SignaturePair.newBuilder()
                        .ecdsaSecp256k1(Bytes.wrap(signedMessage))
                        .pubKeyPrefix(Bytes.wrap(compressedPublicKey))
                        .build())
                .build();
        return SignatureMap.PROTOBUF.toBytes(signatureMap).toByteArray();
    }

    private byte[] buildDefaultScheduleTransactionBodyForCryptoTransferBytes(
            final Entity sender, final Entity receiver) {
        final long transferAmount = 100L;
        final var scheduleBody = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(
                                        AccountAmount.newBuilder()
                                                .accountID(EntityIdUtils.toAccountId(sender))
                                                .amount(-transferAmount)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(EntityIdUtils.toAccountId(receiver))
                                                .amount(transferAmount)
                                                .build())
                                .build()))
                .build();
        return CommonPbjConverters.asBytes(SchedulableTransactionBody.PROTOBUF, scheduleBody);
    }

    private Entity scheduleContractPersist(byte[] runtimeBytecode, Address contractAddress) {
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var contractEvmAddress = toEvmAddress(contractEntityId);
        final var key = com.hederahashgraph.api.proto.java.Key.newBuilder()
                .setContractID(com.hederahashgraph.api.proto.java.ContractID.newBuilder()
                        .setShardNum(contractEntityId.getShard())
                        .setRealmNum(contractEntityId.getRealm())
                        .setContractNum(contractEntityId.getNum()))
                .build()
                .toByteArray();

        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(contractEntityId.getId())
                        .num(contractEntityId.getNum())
                        .evmAddress(contractEvmAddress)
                        .type(CONTRACT)
                        .key(key)
                        .balance(1500L))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(runtimeBytecode))
                .persist();
        domainBuilder.recordFile().customize(f -> f.bytes(runtimeBytecode)).persist();
        return entity;
    }
}
