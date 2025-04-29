package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.HRC755Contract;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.sun.jna.ptr.IntByReference;
import java.nio.ByteBuffer;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Keys;

@RequiredArgsConstructor
class ContractCallContractSignScheduleTest extends AbstractContractCallServiceTest {

    public static byte[] signMessage(final byte[] messageHash, byte[] privateKey) {
        final LibSecp256k1.secp256k1_ecdsa_recoverable_signature signature =
                new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();
        LibSecp256k1.secp256k1_ecdsa_sign_recoverable(CONTEXT, signature, messageHash, privateKey, null, null);

        final ByteBuffer compactSig = ByteBuffer.allocate(64);
        final IntByReference recId = new IntByReference(0);
        LibSecp256k1.secp256k1_ecdsa_recoverable_signature_serialize_compact(
                LibSecp256k1.CONTEXT, compactSig, recId, signature);
        compactSig.flip();
        final byte[] sig = compactSig.array();

        final byte[] result = new byte[65];
        System.arraycopy(sig, 0, result, 0, 64);
        result[64] = (byte) (recId.getValue() + 27);
        return result;
    }

    @Test
    void authorizeScheduleWithContract() {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);
        final var payerAccount = accountEntityPersist();
        final var scheduleEntity = domainBuilder.entity().customize(e -> e.type(EntityType.SCHEDULE)).persist();
        final var scheduleTransactionBody = SchedulableTransactionBody.newBuilder().cryptoTransfer(
                CryptoTransferTransactionBody.DEFAULT).build();
        final var bytes = CommonPbjConverters.asBytes(SchedulableTransactionBody.PROTOBUF, scheduleTransactionBody);
        final var schedule = domainBuilder.schedule().customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                .transactionBody(bytes).payerAccountId(payerAccount.toEntityId())
                .creatorAccountId(payerAccount.toEntityId())
        ).persist();
        // When
        final var entityId = EntityId.of(schedule.getScheduleId());
        final var functionCall = contract.send_authorizeScheduleCall((getAddressFromEntityId(entityId)));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void signScheduleWithContract()
            throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC755Contract::deploy);

        final var payerAccount = accountEntityPersist();
        final var scheduleEntity = domainBuilder.entity().customize(e -> e.type(EntityType.SCHEDULE)).persist();
        final var scheduleTransactionBody = SchedulableTransactionBody.newBuilder().cryptoTransfer(
                CryptoTransferTransactionBody.DEFAULT).build();
        final var bytes = CommonPbjConverters.asBytes(SchedulableTransactionBody.PROTOBUF, scheduleTransactionBody);
        final var schedule = domainBuilder.schedule().customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                .transactionBody(bytes).payerAccountId(payerAccount.toEntityId())
                .creatorAccountId(payerAccount.toEntityId())
        ).persist();

        final var message = getMessageBytes(scheduleEntity);
        final var messageHash = new Keccak.Digest256().digest(message.toByteArray());

        final var keyPair = Keys.createEcKeyPair();
        var privateKey = keyPair.getPrivateKey().toByteArray();
        var publicKey = keyPair.getPublicKey().toByteArray();
        final var signedBytes = signMessage(messageHash, privateKey);

        final var signatureMap = SignatureMap.newBuilder()
                .sigPair(SignaturePair.newBuilder()
                        .ecdsaSecp256k1(Bytes.wrap(signedBytes))
                        .pubKeyPrefix(Bytes.wrap(publicKey))
                        .build())
                .build();

        final var signatureMapBytes =
                SignatureMap.PROTOBUF.toBytes(signatureMap).toByteArray();

        // When
        final var entityId = EntityId.of(schedule.getScheduleId());
        final var functionCall = contract.send_signScheduleCall((getAddressFromEntityId(entityId)), signatureMapBytes);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var result = contract.call_signScheduleCall(
                    (getAddressFromEntityId(entityId)), signatureMapBytes).send();
            assertThat(result).isNotNull();
            //verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    private Bytes getMessageBytes(final Entity entity) {
        final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 3);
        buffer.putLong(entity.getShard());
        buffer.putLong(entity.getRealm());
        buffer.putLong(entity.getNum());
        return Bytes.wrap(buffer.array());
    }
}
