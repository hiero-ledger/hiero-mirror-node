// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class FileServiceTest extends RestJavaIntegrationTest {

    private final FileService service;

    @Test
    void getExchangeRateSuccess() {
        // given
        final var exchangeRateSet = ExchangeRateSet.newBuilder().build();
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.exchangeRateFile()).fileData(exchangeRateSet.toByteArray()))
                .persist();
        final var bound = bound(RangeOperator.EQ, fileData);

        // when
        final var actual = service.getExchangeRate(bound);

        // then
        fileData.setTransactionType(null);
        assertThat(actual).isEqualTo(new SystemFile<>(fileData, exchangeRateSet));
    }

    @Test
    void getExchangeRateNotFound() {
        // given
        final var bound = bound(RangeOperator.GTE, domainBuilder.fileData().get());

        // when / then
        assertThatThrownBy(() -> service.getExchangeRate(bound)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getExchangeRateRecovers() {
        // given
        final var exchangeRateSet = ExchangeRateSet.newBuilder().build();
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.exchangeRateFile()).fileData(exchangeRateSet.toByteArray()))
                .persist();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.exchangeRateFile()).fileData(domainBuilder.bytes(10)))
                .persist();
        final var bound = bound(RangeOperator.GTE, fileData);

        // when
        final var actual = service.getExchangeRate(bound);

        // then
        fileData.setTransactionType(null);
        assertThat(actual).isEqualTo(new SystemFile<>(fileData, exchangeRateSet));
    }

    private Bound bound(RangeOperator operator, org.hiero.mirror.common.domain.file.FileData fileData) {
        final long timestamp = fileData.getConsensusTimestamp();
        final var parameter = new TimestampParameter[] {new TimestampParameter(operator, timestamp)};
        return new Bound(parameter, false, "", FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
    }
}
