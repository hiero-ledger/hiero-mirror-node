/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.evm.contracts.operations;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.AbstractContractCallServiceTest;
import com.hedera.mirror.web3.web3j.generated.SelfDestructContract;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.util.encoders.Hex;

@RequiredArgsConstructor
class SelfDestructOperationTest extends AbstractContractCallServiceTest {

    @Test
    void testSuccessfulExecute() throws Exception {
        final var senderPublicKey = ByteString.copyFrom(
                Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
        final var senderAlias = Address.wrap(
                Bytes.wrap(recoverAddressFromPubKey(senderPublicKey.substring(2).toByteArray())));
        domainBuilder
                .entity()
                .customize(e -> e.evmAddress(senderAlias.toArray()))
                .persist();
        final var contract = testWeb3jService.deployWithValue(SelfDestructContract::deploy, BigInteger.valueOf(1000));
        final var result = contract.send_destructContract(senderAlias.toUnprefixedHexString())
                .send();
        assertThat(result.getContractAddress()).isEqualTo("0x");
    }

    @Test
    void testExecuteWithInvalidOwner() {
        final var systemAccountId = generateSystemAccountId();
        final var systemAccountAddress = toAddress(systemAccountId);
        final var contract = testWeb3jService.deployWithValue(SelfDestructContract::deploy, BigInteger.valueOf(1000));
        final var functionCall = contract.send_destructContract(systemAccountAddress.toUnprefixedHexString());

        MirrorEvmTransactionException exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        assertThat(exception.getMessage()).isEqualTo(INVALID_SOLIDITY_ADDRESS.name());
    }
}
