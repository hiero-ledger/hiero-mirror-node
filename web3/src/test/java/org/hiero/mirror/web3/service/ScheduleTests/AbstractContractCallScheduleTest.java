// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.ScheduleTests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.web3.service.AbstractContractCallServiceHistoricalTest;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.tuples.generated.Tuple2;

abstract class AbstractContractCallScheduleTest extends AbstractContractCallServiceHistoricalTest {

    protected Entity scheduleEntityPersist() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.SCHEDULE))
                .persist();
    }

    protected Schedule schedulePersist(
            final Entity scheduleEntity, final Entity payerAccount, final byte[] transactionBody) {
        return domainBuilder
                .schedule()
                .customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                        .transactionBody(transactionBody)
                        .payerAccountId(payerAccount.toEntityId())
                        .creatorAccountId(payerAccount.toEntityId()))
                .persist();
    }

    protected byte[] buildDefaultScheduleTransactionBody(final Entity sender, final Entity receiver) {
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

    protected void verifyCallFunctionResult(final Tuple2<BigInteger, String> functionCall) {
        // Because we perform eth_call, we cannot validate if the scheduleId is valid or not, we only check the
        // format and the status of the result
        assertThat(functionCall.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
        assertThat(functionCall.component2()).startsWith("0x").hasSize(42).matches("^0x[0-9a-fA-F]+$");
        assertThat(functionCall.component2()).isNotEqualTo(Address.ZERO);
    }
}
