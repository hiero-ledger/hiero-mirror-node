// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.balance;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.Downloader;
import org.hiero.mirror.importer.downloader.NodeSignatureVerifier;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.reader.balance.BalanceFileReader;
import org.hiero.mirror.importer.reader.signature.SignatureFileReader;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;

@Named
public class AccountBalancesDownloader extends Downloader<AccountBalanceFile, AccountBalance> {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AtomicBoolean accountBalanceFileExists = new AtomicBoolean(false);

    @SuppressWarnings("java:S107")
    public AccountBalancesDownloader(
            AccountBalanceFileRepository accountBalanceFileRepository,
            ConsensusNodeService consensusNodeService,
            BalanceDownloaderProperties downloaderProperties,
            ImporterProperties importerProperties,
            MeterRegistry meterRegistry,
            DateRangeCalculator dateRangeCalculator,
            NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier,
            StreamFileProvider streamFileProvider,
            BalanceFileReader streamFileReader) {
        super(
                consensusNodeService,
                downloaderProperties,
                importerProperties,
                meterRegistry,
                dateRangeCalculator,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                streamFileReader);
        this.accountBalanceFileRepository = accountBalanceFileRepository;
    }

    @Override
    protected void onVerified(StreamFileData streamFileData, AccountBalanceFile streamFile, ConsensusNode node) {
        super.onVerified(streamFileData, streamFile, node);
        accountBalanceFileExists.set(true);
    }

    @Override
    protected boolean shouldDownload() {
        if (downloaderProperties.isEnabled()) {
            return true;
        }

        if (accountBalanceFileExists.get()) {
            return false;
        }

        if (accountBalanceFileRepository.findLatest().isPresent()) {
            accountBalanceFileExists.set(true);
            return false;
        }

        return true;
    }
}
