// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.downloader.AbstractDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.reader.balance.ProtoBalanceFileReader;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AccountBalancesDownloaderTest extends AbstractDownloaderTest<AccountBalanceFile> {

    private final CommonProperties commonProperties = new CommonProperties();
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        var properties = new BalanceDownloaderProperties(commonDownloaderProperties);
        properties.setEnabled(true);
        return properties;
    }

    @Override
    protected Downloader<AccountBalanceFile, AccountBalance> getDownloader() {
        BalanceFileReader balanceFileReader = new BalanceFileReaderImplV1(
                new BalanceParserProperties(), new AccountBalanceLineParserV1(commonProperties));
        var streamFileProvider = new S3StreamFileProvider(commonProperties, commonDownloaderProperties, s3AsyncClient);
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
    }

    @Test
    void downloadWithMixedStreamFileExtensions() {
        // for the mixed scenario, both .csv and .pb.gz files exist for the same timestamp; however, all .csv and
        // .csv_sig files are intentionally made empty so if two account balance files are processed, they must be
        // the .pb.gz files
        ProtoBalanceFileReader protoBalanceFileReader = new ProtoBalanceFileReader();
        var streamFileProvider = new S3StreamFileProvider(commonProperties, commonDownloaderProperties, s3AsyncClient);
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
