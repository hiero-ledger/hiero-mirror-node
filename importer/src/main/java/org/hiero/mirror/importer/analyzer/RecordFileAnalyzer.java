// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.analyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.StreamFileReaderException;
import org.hiero.mirror.importer.reader.record.CompositeRecordFileReader;
import org.hiero.mirror.importer.reader.record.ProtoRecordFileReader;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV1;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV2;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV5;

/**
 * Analyzer for *.rcd.gz files that tracks statistics about transactions using signedTransactionBytes.
 *
 * This analyzer:
 * 1. Iterates over all *.rcd.gz files in a directory
 * 2. Parses each file to RecordFile objects
 * 3. Analyzes each RecordItem to track:
 *    - Total RecordItem count
 *    - Count of RecordItems with signedTransactionBytes
 *    - Set of payer account IDs for signed transactions
 *    - Set of transaction types for signed transactions
 * 4. Calculates the ratio of signed transactions vs total transactions
 */
@CustomLog
public class RecordFileAnalyzer {

    private final CompositeRecordFileReader recordFileReader;

    // Counters
    private final AtomicLong totalRecordItems = new AtomicLong(0);
    private final AtomicLong nonSignedTransactionBytesCount = new AtomicLong(0);

    // Sets for tracking unique values (only for transactions WITHOUT signedTransactionBytes)
    private final Set<String> payerAccountIds = new HashSet<>();
    private final Set<String> transactionTypes = new HashSet<>();

    public RecordFileAnalyzer() {
        this.recordFileReader = new CompositeRecordFileReader(
                new RecordFileReaderImplV1(),
                new RecordFileReaderImplV2(),
                new RecordFileReaderImplV5(),
                new ProtoRecordFileReader());
    }

    /**
     * Analyze all *.rcd.gz files in the specified directory.
     *
     * @param directoryPath Path to directory containing *.rcd.gz files
     * @return AnalysisResult containing all statistics
     * @throws IOException if directory access fails
     */
    public AnalysisResult analyzeDirectory(String directoryPath) throws IOException {
        return analyzeDirectory(Paths.get(directoryPath));
    }

    /**
     * Analyze all *.rcd.gz files in the specified directory.
     *
     * @param directoryPath Path to directory containing *.rcd.gz files
     * @return AnalysisResult containing all statistics
     * @throws IOException if directory access fails
     */
    public AnalysisResult analyzeDirectory(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("Directory does not exist or is not a directory: " + directoryPath);
        }

        log.info("Starting analysis of *.rcd.gz files in directory: {}", directoryPath);

        // Reset counters
        resetCounters();

        try (var paths = Files.walk(directoryPath)) {
            List<Path> rcdFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".rcd.gz"))
                    .sorted() // Sort by filename for consistent processing order
                    .toList();

            log.info("Found {} *.rcd.gz files to analyze", rcdFiles.size());

            for (Path rcdFile : rcdFiles) {
                try {
                    analyzeRecordFile(rcdFile);
                } catch (Exception e) {
                    log.error("Failed to analyze file: {}, skipping", rcdFile, e);
                    // Continue processing other files even if one fails
                }
            }
        }

        return createAnalysisResult();
    }

    /**
     * Analyze a single *.rcd.gz file.
     *
     * @param filePath Path to the *.rcd.gz file
     * @throws StreamFileReaderException if parsing fails
     */
    private void analyzeRecordFile(Path filePath) {
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        if (!file.getName().endsWith(".rcd.gz")) {
            throw new IllegalArgumentException("File must be a *.rcd.gz file: " + filePath);
        }

        try {
            log.debug("Analyzing record file: {}", filePath);
            StreamFileData streamFileData = StreamFileData.from(file);
            RecordFile recordFile = recordFileReader.read(streamFileData);

            log.debug("Parsed record file: {} with {} transactions", filePath, recordFile.getCount());

            // Analyze each RecordItem in the file
            analyzeRecordItems(recordFile.getItems());

        } catch (Exception e) {
            log.error("Failed to parse record file: {}", filePath, e);
            throw new StreamFileReaderException("Error parsing record file " + filePath, e);
        }
    }

    /**
     * Analyze a list of RecordItems and update counters and sets.
     *
     * @param recordItems List of RecordItems to analyze
     */
    private void analyzeRecordItems(List<RecordItem> recordItems) {
        for (RecordItem recordItem : recordItems) {
            // Increment total counter
            totalRecordItems.incrementAndGet();

            // Check if this RecordItem has a Transaction WITHOUT signedTransactionBytes
            if (!hasSignedTransactionBytes(recordItem)) {
                nonSignedTransactionBytesCount.incrementAndGet();

                // Extract and store payer account ID
                String payerAccountId = extractPayerAccountId(recordItem);
                if (payerAccountId != null) {
                    synchronized (payerAccountIds) {
                        payerAccountIds.add(payerAccountId);
                    }
                }

                // Extract and store transaction type
                String transactionType = extractTransactionType(recordItem);
                if (transactionType != null) {
                    synchronized (transactionTypes) {
                        transactionTypes.add(transactionType);
                    }
                }
            }
        }
    }

    /**
     * Check if a RecordItem has a Transaction that uses signedTransactionBytes.
     *
     * @param recordItem The RecordItem to check
     * @return true if the transaction uses signedTransactionBytes
     */
    private boolean hasSignedTransactionBytes(RecordItem recordItem) {
        try {
            com.hederahashgraph.api.proto.java.Transaction transaction = recordItem.getTransaction();
            return !transaction.getSignedTransactionBytes().isEmpty();
        } catch (Exception e) {
            log.debug("Error checking signedTransactionBytes for RecordItem: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract the payer account ID from a RecordItem.
     *
     * @param recordItem The RecordItem to extract from
     * @return Payer account ID as string, or null if not found
     */
    private String extractPayerAccountId(RecordItem recordItem) {
        var accountId = recordItem.getPayerAccountId();
        return String.format("%d.%d.%d", accountId.getShard(), accountId.getRealm(), accountId.getNum());
    }

    /**
     * Extract the transaction type from a RecordItem.
     *
     * @param recordItem The RecordItem to extract from
     * @return Transaction type as string, or null if not found
     */
    private String extractTransactionType(RecordItem recordItem) {
        try {
            com.hederahashgraph.api.proto.java.TransactionBody body = recordItem.getTransactionBody();
            if (body != null) {
                // Check each possible transaction type
                if (body.hasCryptoTransfer()) return "CRYPTOTRANSFER";
                if (body.hasCryptoCreateAccount()) return "CRYPTOCREATEACCOUNT";
                if (body.hasCryptoUpdateAccount()) return "CRYPTOUPDATEACCOUNT";
                if (body.hasCryptoDelete()) return "CRYPTODELETE";
                if (body.hasContractCall()) return "CONTRACTCALL";
                if (body.hasContractCreateInstance()) return "CONTRACTCREATEINSTANCE";
                if (body.hasContractUpdateInstance()) return "CONTRACTUPDATEINSTANCE";
                if (body.hasContractDeleteInstance()) return "CONTRACTDELETEINSTANCE";
                if (body.hasFileCreate()) return "FILECREATE";
                if (body.hasFileUpdate()) return "FILEUPDATE";
                if (body.hasFileDelete()) return "FILEDELETE";
                if (body.hasFileAppend()) return "FILEAPPEND";
                if (body.hasConsensusCreateTopic()) return "CONSENSUSCREATETOPIC";
                if (body.hasConsensusUpdateTopic()) return "CONSENSUSUPDATETOPIC";
                if (body.hasConsensusDeleteTopic()) return "CONSENSUSDELETETOPIC";
                if (body.hasConsensusSubmitMessage()) return "CONSENSUSSUBMITMESSAGE";
                if (body.hasTokenCreation()) return "TOKENCREATION";
                if (body.hasTokenFreeze()) return "TOKENFREEZE";
                if (body.hasTokenUnfreeze()) return "TOKENUNFREEZE";
                if (body.hasTokenGrantKyc()) return "TOKENGRANTKYC";
                if (body.hasTokenRevokeKyc()) return "TOKENREVOKEKYC";
                if (body.hasTokenDeletion()) return "TOKENDELETION";
                if (body.hasTokenUpdate()) return "TOKENUPDATE";
                if (body.hasTokenMint()) return "TOKENMINT";
                if (body.hasTokenBurn()) return "TOKENBURN";
                if (body.hasTokenWipe()) return "TOKENWIPE";
                if (body.hasTokenAssociate()) return "TOKENASSOCIATE";
                if (body.hasTokenDissociate()) return "TOKENDISSOCIATE";
                if (body.hasScheduleCreate()) return "SCHEDULECREATE";
                if (body.hasScheduleDelete()) return "SCHEDULEDELETE";
                if (body.hasScheduleSign()) return "SCHEDULESIGN";
                if (body.hasUtilPrng()) return "UTILPRNG";

                return "UNKNOWN";
            }
        } catch (Exception e) {
            log.debug("Error extracting transaction type: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Reset all counters and sets.
     */
    private void resetCounters() {
        totalRecordItems.set(0);
        nonSignedTransactionBytesCount.set(0);
        synchronized (payerAccountIds) {
            payerAccountIds.clear();
        }
        synchronized (transactionTypes) {
            transactionTypes.clear();
        }
    }

    /**
     * Create the final analysis result.
     *
     * @return AnalysisResult containing all collected statistics
     */
    private AnalysisResult createAnalysisResult() {
        long total = totalRecordItems.get();
        long nonSigned = nonSignedTransactionBytesCount.get();
        double ratio = total > 0 ? (double) nonSigned / total * 100.0 : 0.0;

        return AnalysisResult.builder()
                .totalRecordItems(total)
                .nonSignedTransactionBytesCount(nonSigned)
                .nonSignedTransactionRatio(ratio)
                .uniquePayerAccountIds(new HashSet<>(payerAccountIds))
                .uniqueTransactionTypes(new HashSet<>(transactionTypes))
                .build();
    }

    /**
     * Result class containing all analysis statistics.
     */
    @lombok.Builder
    @lombok.Data
    public static class AnalysisResult {
        private final long totalRecordItems;
        private final long nonSignedTransactionBytesCount;
        private final double nonSignedTransactionRatio;
        private final Set<String> uniquePayerAccountIds;
        private final Set<String> uniqueTransactionTypes;

        @Override
        public String toString() {
            return String.format(
                    "Record File Analysis Results:\n" + "  Total RecordItems processed: %,d\n"
                            + "  RecordItems WITHOUT signedTransactionBytes: %,d\n"
                            + "  Percentage WITHOUT signedTransactionBytes: %.2f%%\n"
                            + "  Unique payer account IDs (non-signed): %,d\n"
                            + "  Unique transaction types (non-signed): %,d\n"
                            + "  Payer Account IDs: %s\n"
                            + "  Transaction Types: %s",
                    totalRecordItems,
                    nonSignedTransactionBytesCount,
                    nonSignedTransactionRatio,
                    uniquePayerAccountIds.size(),
                    uniqueTransactionTypes.size(),
                    uniquePayerAccountIds,
                    uniqueTransactionTypes);
        }
    }
}
