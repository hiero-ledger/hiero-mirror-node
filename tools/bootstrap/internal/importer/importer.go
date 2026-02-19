// SPDX-License-Identifier: Apache-2.0

// Package importer handles the PostgreSQL import pipeline including
// table name extraction, partition truncation, and COPY streaming.
package importer

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/klauspost/pgzip"
	"github.com/zeebo/blake3"
	"mirrornode-bootstrap/internal/buffers"
)

var (
	// partitionPattern matches partition suffixes like _p2024_01 or _p2024_01_atma
	partitionPattern = regexp.MustCompile(`^(.*)_p\d{4}_\d{2}(_atma)?$`)
	// partitionDatePattern extracts year and month from partition filenames
	partitionDatePattern = regexp.MustCompile(`_p(\d{4})_(\d{2})`)
)

// ImportResult contains the results of an import operation.
type ImportResult struct {
	RowsImported int64
	BytesRead    int64
	ActualSize   int64  // Actual file size (for size mismatch reporting)
	TableName    string
	ActualHash   string // BLAKE3 hash computed during import
	HashValid    bool   // true if hash matches expected
	Error        error
}

// GetTableName extracts the base table name from a filename.
// Examples:
//   - "account_balance_p2024_01.csv.gz" -> "account_balance"
//   - "flyway_schema_history.csv.gz" -> "flyway_schema_history"
//   - "subdir/account_balance_p2024_01.csv.gz" -> "account_balance"
func GetTableName(filename string) string {
	base := filepath.Base(filename)
	base = strings.TrimSuffix(base, ".csv.gz")
	base = strings.TrimSuffix(base, ".gz")
	base = strings.TrimSuffix(base, ".csv")

	// Check for partition pattern
	if matches := partitionPattern.FindStringSubmatch(base); len(matches) > 0 {
		return matches[1]
	}
	return base
}

// GetTableOrPartition returns the exact table/partition name to use for operations.
// For partitioned tables, returns the partition name; otherwise returns the table name.
func GetTableOrPartition(filename string) string {
	base := filepath.Base(filename)
	return strings.TrimSuffix(strings.TrimSuffix(base, ".csv.gz"), ".csv")
}

// IsPartitioned returns true if the filename represents a partition.
func IsPartitioned(filename string) bool {
	base := filepath.Base(filename)
	base = strings.TrimSuffix(base, ".csv.gz")
	return partitionPattern.MatchString(base)
}

// IsSpecialFile returns true if the file is not a data file (e.g., schema.sql.gz).
func IsSpecialFile(filename string) bool {
	base := filepath.Base(filename)
	return base == "schema.sql.gz" || base == "MIRRORNODE_VERSION.gz"
}

// TruncateBeforeImport truncates the table/partition before import.
func TruncateBeforeImport(ctx context.Context, conn *pgx.Conn, filename string) error {
	target := GetTableOrPartition(filename)

	query := fmt.Sprintf("TRUNCATE TABLE %s", target)
	_, err := conn.Exec(ctx, query)
	return err
}

// TruncateBeforeImportTx truncates within a transaction.
// Returns true if truncation was performed, false if partition doesn't exist.
func TruncateBeforeImportTx(ctx context.Context, tx pgx.Tx, filename string) (bool, error) {
	target := GetTableOrPartition(filename)

	// Check if table/partition exists first to avoid aborting the transaction
	exists, err := relationExists(ctx, tx, target)
	if err != nil {
		return false, err
	}

	if !exists {
		return false, nil
	}

	query := fmt.Sprintf("TRUNCATE TABLE %s", target)
	_, err = tx.Exec(ctx, query)
	if err != nil {
		return false, fmt.Errorf("truncate failed: %w", err)
	}
	return true, nil
}

// relationExists checks if a table or partition exists in the public schema.
func relationExists(ctx context.Context, tx pgx.Tx, name string) (bool, error) {
	var exists bool
	query := `SELECT EXISTS (
		SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = $1
		UNION
		SELECT FROM pg_inherits i
		JOIN pg_class c ON c.oid = i.inhrelid
		WHERE c.relname = $1
	)`
	err := tx.QueryRow(ctx, query, name).Scan(&exists)
	return exists, err
}

// parsePartitionRange extracts the year/month from a partition filename and returns
// the nanosecond timestamp range [startNs, endNs) for that month.
// Returns ok=false if the filename doesn't contain a partition date pattern.
func parsePartitionRange(filename string) (startNs, endNs int64, ok bool) {
	matches := partitionDatePattern.FindStringSubmatch(filepath.Base(filename))
	if len(matches) < 3 {
		return 0, 0, false
	}
	year, _ := strconv.Atoi(matches[1])
	month, _ := strconv.Atoi(matches[2])

	start := time.Date(year, time.Month(month), 1, 0, 0, 0, 0, time.UTC)
	end := start.AddDate(0, 1, 0) // first day of next month
	return start.UnixNano(), end.UnixNano(), true
}

// getTimestampColumn returns the timestamp column to use for range-based deletes.
// Most tables use consensus_timestamp; record_file uses consensus_end.
func getTimestampColumn(ctx context.Context, tx pgx.Tx, tableName string) (string, error) {
	var col string
	query := `SELECT column_name FROM information_schema.columns
		WHERE table_schema = 'public' AND table_name = $1
		AND column_name IN ('consensus_timestamp', 'consensus_end')
		ORDER BY CASE column_name WHEN 'consensus_timestamp' THEN 0 ELSE 1 END
		LIMIT 1`
	err := tx.QueryRow(ctx, query, tableName).Scan(&col)
	if err != nil {
		return "", fmt.Errorf("no timestamp column found for table %s", tableName)
	}
	return col, nil
}

// ImportFile imports a gzipped CSV file into PostgreSQL using COPY.
// Uses parallel decompression and pooled buffers for performance.
// decompressorThreads controls how many parallel goroutines decompress data.
func ImportFile(ctx context.Context, conn *pgx.Conn, filePath string, decompressorThreads int) ImportResult {
	result := ImportResult{}

	// Open gzipped file
	file, err := os.Open(filePath)
	if err != nil {
		result.Error = fmt.Errorf("open failed: %w", err)
		return result
	}
	defer file.Close()

	// Get file info for application_name
	baseName := filepath.Base(filePath)

	// Set application name for progress tracking
	_, err = conn.Exec(ctx, fmt.Sprintf("SET application_name = 'bootstrap_copy_%s'", baseName))
	if err != nil {
		result.Error = fmt.Errorf("set application_name failed: %w", err)
		return result
	}

	// Create parallel decompressor
	// pgzip.NewReaderN(reader, blockSize, numBlocks)
	// 4MB blocks, numBlocks concurrent goroutines for parallel decompression
	if decompressorThreads < 1 {
		decompressorThreads = 4 // sensible default
	}
	decompressor, err := pgzip.NewReaderN(file, 4*1024*1024, decompressorThreads)
	if err != nil {
		result.Error = fmt.Errorf("decompressor failed: %w", err)
		return result
	}
	defer decompressor.Close()

	// Use pooled buffer for reading
	readBuf := buffers.GetDecompressBuffer()
	defer buffers.ReturnDecompressBuffer(readBuf)
	reader := bufio.NewReaderSize(decompressor, len(readBuf))

	// Read and parse header line
	headerLine, err := reader.ReadBytes('\n')
	if err != nil {
		result.Error = fmt.Errorf("header read failed: %w", err)
		return result
	}
	result.BytesRead = int64(len(headerLine))

	// Parse header to get column list
	columns := parseHeaderToColumns(headerLine)

	// Get table name
	tableName := GetTableName(filePath)
	result.TableName = tableName

	// Build COPY command
	copySQL := fmt.Sprintf("COPY %s (%s) FROM STDIN WITH (FORMAT csv)", tableName, columns)

	// Execute COPY - streams directly from reader
	pgConn := conn.PgConn()
	copyResult, err := pgConn.CopyFrom(ctx, reader, copySQL)
	if err != nil {
		result.Error = fmt.Errorf("COPY failed: %w", err)
		return result
	}

	result.RowsImported = copyResult.RowsAffected()
	return result
}

// ImportFileWithTx imports a file within a transaction for atomicity.
func ImportFileWithTx(ctx context.Context, conn *pgx.Conn, filePath string, decompressorThreads int) ImportResult {
	result := ImportResult{}

	// Start transaction
	tx, err := conn.Begin(ctx)
	if err != nil {
		result.Error = fmt.Errorf("begin transaction failed: %w", err)
		return result
	}
	defer tx.Rollback(ctx)

	// Truncate before import (within transaction)
	if _, err := TruncateBeforeImportTx(ctx, tx, filePath); err != nil {
		result.Error = fmt.Errorf("truncate failed: %w", err)
		return result
	}

	// Perform import
	result = ImportFile(ctx, conn, filePath, decompressorThreads)
	if result.Error != nil {
		return result
	}

	// Commit transaction
	if err := tx.Commit(ctx); err != nil {
		result.Error = fmt.Errorf("commit failed: %w", err)
		return result
	}

	return result
}

// ImportWithValidation performs single-pass hash validation and import.
// Uses TeeReader to compute BLAKE3 hash while streaming to PostgreSQL.
// If hash doesn't match expectedHash, the transaction is rolled back.
// expectedFileSize is verified before reading to fail fast on obviously wrong files.
func ImportWithValidation(ctx context.Context, conn *pgx.Conn, filePath string, expectedHash string, expectedSize int64, decompressorThreads int) ImportResult {
	result := ImportResult{}

	// Quick size check first (fast path to reject obviously wrong files)
	info, err := os.Stat(filePath)
	if err != nil {
		result.Error = fmt.Errorf("stat failed: %w", err)
		return result
	}
	result.ActualSize = info.Size()
	if info.Size() != expectedSize {
		result.Error = fmt.Errorf("size mismatch: expected %d bytes, got %d bytes", expectedSize, info.Size())
		return result
	}

	// Open file
	file, err := os.Open(filePath)
	if err != nil {
		result.Error = fmt.Errorf("open failed: %w", err)
		return result
	}
	defer file.Close()

	baseName := filepath.Base(filePath)

	// Start transaction - allows rollback if hash fails
	tx, err := conn.Begin(ctx)
	if err != nil {
		result.Error = fmt.Errorf("begin transaction failed: %w", err)
		return result
	}
	defer tx.Rollback(ctx) // No-op if committed

	// Set application name for progress tracking (within transaction)
	_, err = tx.Exec(ctx, fmt.Sprintf("SET application_name = 'bootstrap_copy_%s'", baseName))
	if err != nil {
		result.Error = fmt.Errorf("set application_name failed: %w", err)
		return result
	}

	// Determine import strategy: truncate partition if it exists, otherwise use staging table
	target := GetTableOrPartition(filePath)
	tableName := GetTableName(filePath)
	result.TableName = tableName

	truncated, err := TruncateBeforeImportTx(ctx, tx, filePath)
	if err != nil {
		result.Error = fmt.Errorf("truncate failed: %w", err)
		return result
	}

	// If partition doesn't exist but this is a partition file, delete the time range
	// from the parent table to clear any stale data from a previous interrupted import
	if !truncated && target != tableName {
		startNs, endNs, ok := parsePartitionRange(filePath)
		if !ok {
			result.Error = fmt.Errorf("cannot parse partition date range from %s", baseName)
			return result
		}
		tsCol, err := getTimestampColumn(ctx, tx, tableName)
		if err != nil {
			result.Error = err
			return result
		}
		deleteSQL := fmt.Sprintf("DELETE FROM %s WHERE %s >= $1 AND %s < $2", tableName, tsCol, tsCol)
		if _, err := tx.Exec(ctx, deleteSQL, startNs, endNs); err != nil {
			result.Error = fmt.Errorf("delete range failed for %s.%s [%d, %d): %w", tableName, tsCol, startNs, endNs, err)
			return result
		}
	}

	// Create hasher - computes BLAKE3 on compressed data as we read
	hasher := blake3.New()

	// TeeReader: every byte read from file also goes to hasher
	teeReader := io.TeeReader(file, hasher)

	// Create parallel decompressor on top of teeReader
	if decompressorThreads < 1 {
		decompressorThreads = 4
	}
	decompressor, err := pgzip.NewReaderN(teeReader, 4*1024*1024, decompressorThreads)
	if err != nil {
		result.Error = fmt.Errorf("decompressor failed: %w", err)
		return result
	}
	defer decompressor.Close()

	// Use pooled buffer for reading
	readBuf := buffers.GetDecompressBuffer()
	defer buffers.ReturnDecompressBuffer(readBuf)
	reader := bufio.NewReaderSize(decompressor, len(readBuf))

	// Read and parse header line
	headerLine, err := reader.ReadBytes('\n')
	if err != nil {
		result.Error = fmt.Errorf("header read failed: %w", err)
		return result
	}
	result.BytesRead = int64(len(headerLine))

	// Parse header to get column list
	columns := parseHeaderToColumns(headerLine)

	// Build COPY command
	copySQL := fmt.Sprintf("COPY %s (%s) FROM STDIN WITH (FORMAT csv)", tableName, columns)

	// Execute COPY within transaction - use tx.Conn().PgConn() to ensure
	// the COPY is part of the transaction and connection is properly cleaned up
	pgConn := tx.Conn().PgConn()
	copyResult, err := pgConn.CopyFrom(ctx, reader, copySQL)
	if err != nil {
		result.Error = fmt.Errorf("COPY failed: %w", err)
		return result
	}

	result.RowsImported = copyResult.RowsAffected()

	// COPY complete - now verify hash
	result.ActualHash = fmt.Sprintf("%x", hasher.Sum(nil))
	result.HashValid = (result.ActualHash == expectedHash)

	if !result.HashValid {
		// Hash mismatch - rollback transaction (data was corrupted)
		result.Error = fmt.Errorf("hash mismatch: expected %s, got %s", expectedHash, result.ActualHash)
		return result // defer will rollback
	}

	// Hash matches - commit transaction
	if err := tx.Commit(ctx); err != nil {
		result.Error = fmt.Errorf("commit failed: %w", err)
		return result
	}

	return result
}

// parseHeaderToColumns converts a CSV header line to a quoted column list.
func parseHeaderToColumns(header []byte) string {
	header = trimRight(header, '\r', '\n')

	var columns []string
	var current strings.Builder
	inQuotes := false

	for i := 0; i < len(header); i++ {
		ch := header[i]
		switch {
		case ch == '"':
			inQuotes = !inQuotes
		case ch == ',' && !inQuotes:
			columns = append(columns, fmt.Sprintf(`"%s"`, current.String()))
			current.Reset()
		default:
			current.WriteByte(ch)
		}
	}
	columns = append(columns, fmt.Sprintf(`"%s"`, current.String()))

	return strings.Join(columns, ",")
}

// trimRight trims specified bytes from the right side.
func trimRight(b []byte, chars ...byte) []byte {
	for len(b) > 0 {
		found := false
		for _, c := range chars {
			if b[len(b)-1] == c {
				b = b[:len(b)-1]
				found = true
				break
			}
		}
		if !found {
			break
		}
	}
	return b
}

// StreamingReader wraps a decompressed stream with metrics tracking.
type StreamingReader struct {
	reader    io.Reader
	bytesRead int64
}

func NewStreamingReader(r io.Reader) *StreamingReader {
	return &StreamingReader{reader: r}
}

func (s *StreamingReader) Read(p []byte) (int, error) {
	n, err := s.reader.Read(p)
	s.bytesRead += int64(n)
	return n, err
}

func (s *StreamingReader) BytesRead() int64 {
	return s.bytesRead
}
