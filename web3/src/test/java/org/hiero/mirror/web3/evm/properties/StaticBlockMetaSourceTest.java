// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.exception.MissingResultException;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaSourceTest {
    @Mock
    private RecordFileRepository repository;

    private MockedStatic<ContractCallContext> staticMock;

    @Mock
    private ContractCallContext contractCallContext;

    private StaticBlockMetaSource subject;
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @BeforeEach
    void setUp() {
        subject = new StaticBlockMetaSource(repository);
        staticMock = mockStatic(ContractCallContext.class);
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @AfterEach
    void clean() {
        staticMock.close();
    }

    @Test
    void getBlockHashReturnsCorrectValue() {
        final var fileHash =
                "37313862636664302d616365352d343861632d396430612d36393036316337656236626333336466323864652d346100";
        final var recordFile = new RecordFile();
        recordFile.setHash(fileHash);

        given(repository.findByIndex(1)).willReturn(Optional.of(recordFile));
        final var expected = Hash.fromHexString("0x37313862636664302d616365352d343861632d396430612d3639303631633765");
        assertThat(subject.blockHashOf(any(), 1)).isEqualTo(expected);
    }

    @Test
    void getBlockHashThrowsExceptionWhitMissingFileId() {
        given(repository.findByIndex(1)).willReturn(Optional.empty());
        assertThatThrownBy(() -> subject.blockHashOf(any(), 1)).isInstanceOf(MissingResultException.class);
    }

    @Test
    void computeBlockValuesWithCorrectValue() {
        final var recordFile = domainBuilder.recordFile().get();
        final var timeStamp = Instant.ofEpochSecond(0, recordFile.getConsensusStart());
        given(contractCallContext.getRecordFile()).willReturn(recordFile);
        final var result = subject.blockValuesOf(23L);
        assertThat(result.getGasLimit()).isEqualTo(23);
        assertThat(result.getNumber()).isEqualTo(recordFile.getIndex());
        assertThat(result.getTimestamp()).isEqualTo(timeStamp.getEpochSecond());
    }

    @Test
    void computeBlockValuesFailsFailsForMissingFileId() {
        given(ContractCallContext.get()).willReturn(contractCallContext);
        given(contractCallContext.getRecordFile()).willReturn(null);
        assertThatThrownBy(() -> subject.blockValuesOf(1)).isInstanceOf(MissingResultException.class);
    }

    @Test
    void testEthHashFromReturnsCorrectValue() {
        final var result = StaticBlockMetaSource.ethHashFrom(
                "37313862636664302d616365352d343861632d396430612d36393036316337656236626333336466323864652d346100");
        final var expected = Hash.wrap(
                Bytes32.wrap(Bytes.fromHexString("0x37313862636664302d616365352d343861632d396430612d3639303631633765")
                        .toArrayUnsafe()));
        assertThat(result).isEqualTo(expected);
    }
}
