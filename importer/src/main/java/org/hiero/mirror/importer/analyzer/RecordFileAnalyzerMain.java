// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.analyzer;

import java.io.IOException;
import lombok.CustomLog;

/**
 * Main class for running the RecordFileAnalyzer from command line.
 *
 * Usage:
 *   java RecordFileAnalyzerMain <directory-path>
 *
 * Where <directory-path> is the path to a directory containing *.rcd.gz files.
 */
@CustomLog
public class RecordFileAnalyzerMain {

    public static void main(String[] args) {
        String directoryPath = "/Users/ivankavaldzhiev/S3_Record_Files";

        try {
            log.info("Starting RecordFileAnalyzer for directory: {}", directoryPath);

            RecordFileAnalyzer analyzer = new RecordFileAnalyzer();
            RecordFileAnalyzer.AnalysisResult result = analyzer.analyzeDirectory(directoryPath);

            // Print results
            System.out.println("\n" + result.toString());

            // Print additional insights
            printInsights(result);

            log.info("Analysis completed successfully");

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            log.error("IO error during analysis: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            log.error("Unexpected error during analysis", e);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java RecordFileAnalyzerMain <directory-path>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  directory-path  Path to directory containing *.rcd.gz files");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java RecordFileAnalyzerMain /path/to/record/files/");
        System.out.println();
        System.out.println("This analyzer will:");
        System.out.println("  1. Find all *.rcd.gz files in the specified directory");
        System.out.println("  2. Parse each file and extract RecordItems");
        System.out.println("  3. Count total RecordItems and those NOT using signedTransactionBytes");
        System.out.println("  4. Collect unique payer account IDs and transaction types for non-signed transactions");
        System.out.println("  5. Calculate the percentage of transactions NOT using signedTransactionBytes");
    }

    private static void printInsights(RecordFileAnalyzer.AnalysisResult result) {
        System.out.println("\n=== Analysis Insights ===");

        // Ratio insights
        double ratio = result.getNonSignedTransactionRatio();
        if (ratio == 0.0) {
            System.out.println("• All transactions use signedTransactionBytes");
        } else if (ratio < 10.0) {
            System.out.println("• Low usage of non-signed transactions (" + String.format("%.2f", ratio) + "%)");
        } else if (ratio < 50.0) {
            System.out.println("• Moderate usage of non-signed transactions (" + String.format("%.2f", ratio) + "%)");
        } else if (ratio < 90.0) {
            System.out.println("• High usage of non-signed transactions (" + String.format("%.2f", ratio) + "%)");
        } else {
            System.out.println("• Very high usage of non-signed transactions (" + String.format("%.2f", ratio) + "%)");
        }

        // Payer account insights
        int uniquePayers = result.getUniquePayerAccountIds().size();
        if (uniquePayers == 0) {
            System.out.println("• No unique payer accounts found in non-signed transactions");
        } else if (uniquePayers == 1) {
            System.out.println("• All non-signed transactions are from a single payer account");
        } else {
            System.out.println("• Non-signed transactions involve " + uniquePayers + " different payer accounts");
        }

        // Transaction type insights
        int uniqueTypes = result.getUniqueTransactionTypes().size();
        if (uniqueTypes == 0) {
            System.out.println("• No transaction types found in non-signed transactions");
        } else if (uniqueTypes == 1) {
            System.out.println("• All non-signed transactions are of the same type");
        } else {
            System.out.println("• Non-signed transactions involve " + uniqueTypes + " different transaction types");
        }

        // Volume insights
        long total = result.getTotalRecordItems();
        long nonSigned = result.getNonSignedTransactionBytesCount();
        if (total > 0) {
            System.out.println("• Processing volume: " + String.format("%,d", total) + " total transactions, "
                    + String.format("%,d", nonSigned) + " non-signed transactions");
        }
    }
}
