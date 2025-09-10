// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.analyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.CustomLog;

/**
 * Example class demonstrating how to use the RecordFileAnalyzer programmatically.
 */
@CustomLog
public class RecordFileAnalyzerExample {

    public static void main(String[] args) {
        RecordFileAnalyzerExample example = new RecordFileAnalyzerExample();

        // Example: Analyze a directory of *.rcd.gz files
        example.analyzeDirectoryExample();

        // Example: Handle different scenarios
        example.handleDifferentScenarios();
    }

    /**
     * Example: Analyze a directory of *.rcd.gz files
     */
    public void analyzeDirectoryExample() {
        log.info("=== Example: Analyze Directory ===");

        try {
            String directoryPath = "/path/to/your/rcd/files/";

            // Check if directory exists before analyzing
            Path path = Paths.get(directoryPath);
            if (!path.toFile().exists() || !path.toFile().isDirectory()) {
                log.warn("Directory does not exist: {}, skipping example", directoryPath);
                return;
            }

            RecordFileAnalyzer analyzer = new RecordFileAnalyzer();
            RecordFileAnalyzer.AnalysisResult result = analyzer.analyzeDirectory(directoryPath);

            // Print the results
            System.out.println(result.toString());

            // Access individual statistics
            log.info("Total RecordItems: {}", result.getTotalRecordItems());
            log.info("Non-Signed TransactionBytes Count: {}", result.getNonSignedTransactionBytesCount());
            log.info("Non-Signed Transaction Ratio: {:.2f}%", result.getNonSignedTransactionRatio());
            log.info(
                    "Unique Payer Account IDs (non-signed): {}",
                    result.getUniquePayerAccountIds().size());
            log.info(
                    "Unique Transaction Types (non-signed): {}",
                    result.getUniqueTransactionTypes().size());

        } catch (IOException e) {
            log.error("Error analyzing directory", e);
        }
    }

    /**
     * Example: Handle different analysis scenarios
     */
    public void handleDifferentScenarios() {
        log.info("=== Example: Handle Different Scenarios ===");

        // Scenario 1: Empty directory
        handleEmptyDirectory();

        // Scenario 2: Directory with no *.rcd.gz files
        handleDirectoryWithNoRcdFiles();

        // Scenario 3: Directory with mixed file types
        handleDirectoryWithMixedFiles();
    }

    private void handleEmptyDirectory() {
        log.info("--- Scenario: Empty Directory ---");

        try {
            String emptyDirPath = "/path/to/empty/directory/";
            Path path = Paths.get(emptyDirPath);

            if (!path.toFile().exists()) {
                log.info("Empty directory does not exist, creating example scenario");
                // In real usage, you would handle this case
                return;
            }

            RecordFileAnalyzer analyzer = new RecordFileAnalyzer();
            RecordFileAnalyzer.AnalysisResult result = analyzer.analyzeDirectory(emptyDirPath);

            log.info("Empty directory analysis result: {}", result);

        } catch (IOException e) {
            log.error("Error handling empty directory", e);
        }
    }

    private void handleDirectoryWithNoRcdFiles() {
        log.info("--- Scenario: Directory with No *.rcd.gz Files ---");

        try {
            String noRcdDirPath = "/path/to/directory/with/no/rcd/files/";
            Path path = Paths.get(noRcdDirPath);

            if (!path.toFile().exists()) {
                log.info("Directory does not exist, creating example scenario");
                return;
            }

            RecordFileAnalyzer analyzer = new RecordFileAnalyzer();
            RecordFileAnalyzer.AnalysisResult result = analyzer.analyzeDirectory(noRcdDirPath);

            log.info("No *.rcd.gz files found, result: {}", result);

        } catch (IOException e) {
            log.error("Error handling directory with no *.rcd.gz files", e);
        }
    }

    private void handleDirectoryWithMixedFiles() {
        log.info("--- Scenario: Directory with Mixed File Types ---");

        try {
            String mixedDirPath = "/path/to/directory/with/mixed/files/";
            Path path = Paths.get(mixedDirPath);

            if (!path.toFile().exists()) {
                log.info("Directory does not exist, creating example scenario");
                return;
            }

            RecordFileAnalyzer analyzer = new RecordFileAnalyzer();
            RecordFileAnalyzer.AnalysisResult result = analyzer.analyzeDirectory(mixedDirPath);

            log.info("Mixed files directory analysis result: {}", result);

        } catch (IOException e) {
            log.error("Error handling directory with mixed files", e);
        }
    }

    /**
     * Example: Custom analysis with additional processing
     */
    public void customAnalysisExample() {
        log.info("=== Example: Custom Analysis ===");

        try {
            String directoryPath = "/path/to/your/rcd/files/";

            RecordFileAnalyzer analyzer = new RecordFileAnalyzer();
            RecordFileAnalyzer.AnalysisResult result = analyzer.analyzeDirectory(directoryPath);

            // Custom processing based on results
            if (result.getNonSignedTransactionRatio() > 50.0) {
                log.info("High usage of non-signed transactions detected");

                // Analyze payer account distribution
                if (result.getUniquePayerAccountIds().size() > 10) {
                    log.info("Non-signed transactions are distributed across many payer accounts");
                } else {
                    log.info("Non-signed transactions are concentrated in few payer accounts");
                }

                // Analyze transaction type distribution
                if (result.getUniqueTransactionTypes().size() > 5) {
                    log.info("Diverse transaction types in non-signed transactions");
                } else {
                    log.info("Limited transaction types in non-signed transactions");
                }
            } else {
                log.info("Low usage of non-signed transactions");
            }

        } catch (IOException e) {
            log.error("Error in custom analysis", e);
        }
    }
}
