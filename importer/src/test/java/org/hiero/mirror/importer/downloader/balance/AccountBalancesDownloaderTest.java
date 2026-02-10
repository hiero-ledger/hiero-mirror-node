// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.ConsensusNodeStub;
import org.hiero.mirror.importer.downloader.AbstractDownloaderTest;
import org.hiero.mirror.importer.downloader.Downloader;
import org.hiero.mirror.importer.downloader.DownloaderProperties;
import org.hiero.mirror.importer.downloader.provider.S3StreamFileProvider;
import org.hiero.mirror.importer.parser.balance.BalanceParserProperties;
import org.hiero.mirror.importer.reader.balance.BalanceFileReader;
import org.hiero.mirror.importer.reader.balance.BalanceFileReaderImplV1;
import org.hiero.mirror.importer.reader.balance.ProtoBalanceFileReader;
import org.hiero.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

final class AccountBalancesDownloaderTest extends AbstractDownloaderTest<AccountBalanceFile> {

    private final CommonProperties commonProperties = new CommonProperties();

    @Mock
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Override
    protected EntityId entityNum(long num) {
        return EntityId.of(0, 0, num);
    }

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        var properties = new BalanceDownloaderProperties(commonDownloaderProperties);
        properties.setEnabled(true);
        return properties;
    }

    @Override
    protected Downloader<AccountBalanceFile, AccountBalance> getDownloader() {
        BalanceFileReader balanceFileReader =
                new BalanceFileReaderImplV1(new BalanceParserProperties(), new AccountBalanceLineParserV1());
        var streamFileProvider = new S3StreamFileProvider(
                blockBucketProperties, commonProperties, commonDownloaderProperties, s3AsyncClient);
        return new AccountBalancesDownloader(
                accountBalanceFileRepository,
                consensusNodeService,
                (BalanceDownloaderProperties) downloaderProperties,
                importerProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                balanceFileReader);
    }

    @Override
    protected Path getTestDataDir() {
        return Path.of("accountBalances", "v1");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofMinutes(15L);
    }

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setTestFilesAndInstants(
                List.of("2019-08-30T18_15_00.016002001Z_Balances.csv", "2019-08-30T18_30_00.010147001Z_Balances.csv"));

        // account balance files will never exist in non-zero realm / shard network. besides, all test account balance
        // files have 0 shard and realm, it's tedious and with no value to rewrite the test files with correct shard
        // and realm
        commonProperties.setRealm(0);
        commonProperties.setShard(0);

        fileCopier.setIgnoreNonZeroRealmShard(true);

        // recreate nodes with shard=0 and realm=0
        var newNodes = nodes.stream()
                .filter(n -> {
                    var nodeAccountId = n.getNodeAccountId();
                    return nodeAccountId.getShard() != 0 || nodeAccountId.getRealm() != 0;
                })
                .map(n -> ConsensusNodeStub.builder()
                        .nodeAccountId(EntityId.of(0, 0, n.getNodeAccountId().getNum()))
                        .nodeId(n.getNodeId())
                        .publicKey(n.getPublicKey())
                        .stake(n.getStake())
                        .totalStake(n.getTotalStake())
                        .build())
                .toList();
        if (!newNodes.isEmpty()) {
            nodes.clear();
            nodes.addAll(newNodes);
        }
    }

    @Test
    void downloadWithMixedStreamFileExtensions() {
        // for the mixed scenario, both .csv and .pb.gz files exist for the same timestamp; however, all .csv and
        // .csv_sig files are intentionally made empty so if two account balance files are processed, they must be
        // the .pb.gz files
        ProtoBalanceFileReader protoBalanceFileReader = new ProtoBalanceFileReader();
        var streamFileProvider = new S3StreamFileProvider(
                blockBucketProperties, commonProperties, commonDownloaderProperties, s3AsyncClient);
        downloader = new AccountBalancesDownloader(
                accountBalanceFileRepository,
                consensusNodeService,
                (BalanceDownloaderProperties) downloaderProperties,
                importerProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                protoBalanceFileReader);
        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path)
                .from(Path.of("accountBalances", "mixed"))
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath());
        setTestFilesAndInstants(
                List.of("2021-03-10T22_12_56.075092Z_Balances.pb.gz", "2021-03-10T22_27_56.236886Z_Balances.pb.gz"));
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyForSuccess();
        assertThat(importerProperties.getDataPath()).isEmptyDirectory();
    }

    @Test
    void downloadWhenDisabled() {
        // given
        downloaderProperties.setEnabled(false);
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        // when
        downloader.download();

        // then
        verifyStreamFiles(List.of(file1));

        // when
        reset(streamFileNotifier);
        downloader.download();

        // then no new files are downloaded
        verifyStreamFiles(Collections.emptyList());
    }

    @Test
    void downloadWhenDisabledAndAccountBalanceFileAlreadyExists() {
        // given
        downloaderProperties.setEnabled(false);
        when(accountBalanceFileRepository.findLatest())
                .thenReturn(Optional.of(domainBuilder.accountBalanceFile().get()));
        fileCopier.copy();

        // when
        downloader.download();

        // then
        verifyStreamFiles(Collections.emptyList());
        verify(accountBalanceFileRepository).findLatest();

        // when download again
        downloader.download();

        // then
        verifyStreamFiles(Collections.emptyList());
        verify(accountBalanceFileRepository).findLatest(); // no more findLatest calls
    }
}
