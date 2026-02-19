// SPDX-License-Identifier: Apache-2.0

package progress

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestTruncateFilename(t *testing.T) {
	tests := []struct {
		filename string
		maxLen   int
		expected string
	}{
		{"short.csv", 20, "short.csv"},
		{"this_is_a_very_long_filename_that_needs_truncation.csv.gz", 30, "...s_truncation.csv.gz"},
		{"exact", 5, "exact"},
		{"toolong", 5, "...ng"},
		{"", 10, ""},
		{"abc", 10, "abc"},
	}

	for _, tc := range tests {
		result := truncateFilename(tc.filename, tc.maxLen)
		if len(result) > tc.maxLen && tc.filename != "" {
			t.Errorf("truncateFilename(%q, %d): result too long: %q (len=%d)",
				tc.filename, tc.maxLen, result, len(result))
		}
	}
}

func TestFormatNumber(t *testing.T) {
	tests := []struct {
		n        int64
		expected string
	}{
		{0, "0"},
		{999, "999"},
		{1000, "1,000"},
		{1234567, "1,234,567"},
		{1000000000, "1,000,000,000"},
		{-1234, "-1,234"},
	}

	for _, tc := range tests {
		result := FormatNumber(tc.n)
		if result != tc.expected {
			t.Errorf("FormatNumber(%d): expected %q, got %q", tc.n, tc.expected, result)
		}
	}
}

func TestFormatRate(t *testing.T) {
	tests := []struct {
		rate     int64
		expected string
	}{
		{0, "0/s"},
		{1000, "1,000/s"},
		{50000, "50,000/s"},
		{1234567, "1,234,567/s"},
	}

	for _, tc := range tests {
		result := FormatRate(tc.rate)
		if result != tc.expected {
			t.Errorf("FormatRate(%d): expected %q, got %q", tc.rate, tc.expected, result)
		}
	}
}

func TestFormatPercentage(t *testing.T) {
	tests := []struct {
		pct      float64
		expected string
	}{
		{0, "0.00%"},
		{50.5, "50.50%"},
		{100, "100.00%"},
		{33.333, "33.33%"},
		{99.999, "100.00%"},
	}

	for _, tc := range tests {
		result := FormatPercentage(tc.pct)
		if result != tc.expected {
			t.Errorf("FormatPercentage(%f): expected %q, got %q", tc.pct, tc.expected, result)
		}
	}
}

func TestFileProgress_Struct(t *testing.T) {
	fp := FileProgress{
		Filename:      "test.csv.gz",
		RowsProcessed: 1000,
		TotalRows:     5000,
		Percentage:    20.0,
		Rate:          500,
	}

	if fp.Filename != "test.csv.gz" {
		t.Error("Filename not set correctly")
	}
	if fp.RowsProcessed != 1000 {
		t.Error("RowsProcessed not set correctly")
	}
	if fp.TotalRows != 5000 {
		t.Error("TotalRows not set correctly")
	}
	if fp.Percentage != 20.0 {
		t.Error("Percentage not set correctly")
	}
	if fp.Rate != 500 {
		t.Error("Rate not set correctly")
	}
}

func TestProgressState_Struct(t *testing.T) {
	ps := progressState{
		rows: 1000,
		time: time.Now(),
	}

	if ps.rows != 1000 {
		t.Error("rows not set correctly")
	}
	if ps.time.IsZero() {
		t.Error("time should be set")
	}
}

func TestNewMonitor(t *testing.T) {
	// Test with nil connection (valid for testing struct creation)
	m := NewMonitor(nil, 10, "/tmp/progress.txt")

	if m == nil {
		t.Fatal("NewMonitor returned nil")
	}
	if m.interval != 10 {
		t.Errorf("interval: expected 10, got %d", m.interval)
	}
	if m.progressFile != "/tmp/progress.txt" {
		t.Errorf("progressFile: expected /tmp/progress.txt, got %s", m.progressFile)
	}
	if m.dbTable != "bootstrap_manifest_progress" {
		t.Errorf("dbTable: expected bootstrap_manifest_progress, got %s", m.dbTable)
	}
	if m.lastState == nil {
		t.Error("lastState should be initialized")
	}
	if m.printer == nil {
		t.Error("printer should be initialized")
	}
}

func TestNewMonitor_ZeroInterval(t *testing.T) {
	m := NewMonitor(nil, 0, "")

	if m.interval != 0 {
		t.Errorf("interval: expected 0, got %d", m.interval)
	}
}

func TestNewMonitor_EmptyProgressFile(t *testing.T) {
	m := NewMonitor(nil, 5*time.Second, "")

	if m.progressFile != "" {
		t.Errorf("progressFile should be empty, got %s", m.progressFile)
	}
}

func TestWriteProgressFile_Empty(t *testing.T) {
	tmpDir := t.TempDir()
	progressFile := filepath.Join(tmpDir, "progress.txt")

	m := NewMonitor(nil, 10, progressFile)

	// Empty progress
	err := m.WriteProgressFile([]FileProgress{})
	if err != nil {
		t.Fatalf("WriteProgressFile failed: %v", err)
	}

	// Verify file was created
	if _, err := os.Stat(progressFile); os.IsNotExist(err) {
		t.Error("Progress file was not created")
	}

	// Read file content
	content, err := os.ReadFile(progressFile)
	if err != nil {
		t.Fatalf("Failed to read progress file: %v", err)
	}

	// Should have header but no data rows
	if len(content) == 0 {
		t.Error("Progress file is empty")
	}
}

func TestWriteProgressFile_WithData(t *testing.T) {
	tmpDir := t.TempDir()
	progressFile := filepath.Join(tmpDir, "progress.txt")

	m := NewMonitor(nil, 10, progressFile)

	progresses := []FileProgress{
		{
			Filename:      "account_balance_p2024_01.csv.gz",
			RowsProcessed: 500000,
			TotalRows:     1000000,
			Percentage:    50.0,
			Rate:          10000,
		},
		{
			Filename:      "transaction_p2024_01.csv.gz",
			RowsProcessed: 250000,
			TotalRows:     500000,
			Percentage:    50.0,
			Rate:          5000,
		},
	}

	err := m.WriteProgressFile(progresses)
	if err != nil {
		t.Fatalf("WriteProgressFile failed: %v", err)
	}

	// Read and verify content
	content, err := os.ReadFile(progressFile)
	if err != nil {
		t.Fatalf("Failed to read progress file: %v", err)
	}

	contentStr := string(content)

	// Check that filenames are present
	if !strings.Contains(contentStr, "account_balance") {
		t.Error("Progress file should contain account_balance")
	}
	if !strings.Contains(contentStr, "transaction") {
		t.Error("Progress file should contain transaction")
	}

	// Check that headers are present
	if !strings.Contains(contentStr, "Filename") {
		t.Error("Progress file should contain header")
	}
	if !strings.Contains(contentStr, "Rows_Processed") {
		t.Error("Progress file should contain Rows_Processed header")
	}
	if !strings.Contains(contentStr, "Total_Rows") {
		t.Error("Progress file should contain Total_Rows header")
	}
	if !strings.Contains(contentStr, "Percentage") {
		t.Error("Progress file should contain Percentage header")
	}
	if !strings.Contains(contentStr, "Rate") {
		t.Error("Progress file should contain Rate header")
	}
}

func TestWriteProgressFile_NoFile(t *testing.T) {
	// Test with empty progressFile path
	m := NewMonitor(nil, 10, "")

	// Should return nil (no error) when progressFile is empty
	err := m.WriteProgressFile([]FileProgress{})
	if err != nil {
		t.Errorf("WriteProgressFile should return nil for empty path: %v", err)
	}
}

func TestWriteProgressFile_InvalidPath(t *testing.T) {
	// Test with invalid path
	m := NewMonitor(nil, 10, "/nonexistent/directory/progress.txt")

	err := m.WriteProgressFile([]FileProgress{})
	if err == nil {
		t.Error("WriteProgressFile should fail for invalid path")
	}
}

func TestWriteProgressFile_LongFilename(t *testing.T) {
	tmpDir := t.TempDir()
	progressFile := filepath.Join(tmpDir, "progress.txt")

	m := NewMonitor(nil, 10, progressFile)

	// Create a progress with a very long filename
	progresses := []FileProgress{
		{
			Filename:      "this_is_a_very_long_filename_that_should_be_truncated_when_displayed_in_the_progress_file.csv.gz",
			RowsProcessed: 1000,
			TotalRows:     5000,
			Percentage:    20.0,
			Rate:          500,
		},
	}

	err := m.WriteProgressFile(progresses)
	if err != nil {
		t.Fatalf("WriteProgressFile failed: %v", err)
	}

	// Verify file was created
	if _, err := os.Stat(progressFile); os.IsNotExist(err) {
		t.Error("Progress file was not created")
	}
}

func TestWriteProgressFile_ZeroValues(t *testing.T) {
	tmpDir := t.TempDir()
	progressFile := filepath.Join(tmpDir, "progress.txt")

	m := NewMonitor(nil, 10, progressFile)

	progresses := []FileProgress{
		{
			Filename:      "file.csv.gz",
			RowsProcessed: 0,
			TotalRows:     0,
			Percentage:    0,
			Rate:          0,
		},
	}

	err := m.WriteProgressFile(progresses)
	if err != nil {
		t.Fatalf("WriteProgressFile failed: %v", err)
	}
}

func TestWriteProgressFile_LargeNumbers(t *testing.T) {
	tmpDir := t.TempDir()
	progressFile := filepath.Join(tmpDir, "progress.txt")

	m := NewMonitor(nil, 10, progressFile)

	progresses := []FileProgress{
		{
			Filename:      "large.csv.gz",
			RowsProcessed: 999999999999,
			TotalRows:     999999999999,
			Percentage:    99.99,
			Rate:          999999999,
		},
	}

	err := m.WriteProgressFile(progresses)
	if err != nil {
		t.Fatalf("WriteProgressFile failed: %v", err)
	}

	content, err := os.ReadFile(progressFile)
	if err != nil {
		t.Fatalf("Failed to read progress file: %v", err)
	}

	// Large numbers should be formatted with commas
	if !strings.Contains(string(content), ",") {
		t.Error("Large numbers should be formatted with commas")
	}
}

func TestTruncateFilename_EdgeCases(t *testing.T) {
	// Very short maxLen (edge case: must fit "...")
	result := truncateFilename("longfilename.csv.gz", 3)
	// When maxLen < 4, the result may be just "..."
	if len(result) > 3 {
		t.Errorf("Result should be max 3 chars, got %d: %s", len(result), result)
	}

	// maxLen of 4 (minimum to show any of the filename)
	result = truncateFilename("longfilename.csv.gz", 4)
	if len(result) > 4 {
		t.Errorf("Result should be max 4 chars, got %d: %s", len(result), result)
	}

	// Exactly at limit
	result = truncateFilename("12345", 5)
	if result != "12345" {
		t.Errorf("Should not truncate exact length: %s", result)
	}

	// Below limit
	result = truncateFilename("abc", 10)
	if result != "abc" {
		t.Errorf("Should not truncate below length: %s", result)
	}

	// Empty filename
	result = truncateFilename("", 10)
	if result != "" {
		t.Errorf("Empty filename should remain empty: %s", result)
	}

	// Unicode filename
	result = truncateFilename("αβγδεζηθικ.csv.gz", 10)
	// Note: We're counting bytes, not runes, so this may be tricky
	// Just ensure it doesn't panic
}

func TestFormatNumber_EdgeCases(t *testing.T) {
	// Maximum int64
	result := FormatNumber(9223372036854775807)
	if !strings.Contains(result, ",") {
		t.Error("Large number should have commas")
	}

	// Minimum int64
	result = FormatNumber(-9223372036854775808)
	if !strings.HasPrefix(result, "-") {
		t.Error("Negative number should have minus sign")
	}
}

func TestFormatPercentage_EdgeCases(t *testing.T) {
	// Very small percentage
	result := FormatPercentage(0.001)
	if result != "0.00%" {
		t.Errorf("Very small percentage: expected 0.00%%, got %s", result)
	}

	// Very large percentage
	result = FormatPercentage(1000.5)
	if result != "1000.50%" {
		t.Errorf("Large percentage: expected 1000.50%%, got %s", result)
	}

	// Negative percentage
	result = FormatPercentage(-10.5)
	if result != "-10.50%" {
		t.Errorf("Negative percentage: expected -10.50%%, got %s", result)
	}
}

func BenchmarkFormatNumber(b *testing.B) {
	for i := 0; i < b.N; i++ {
		FormatNumber(1234567890)
	}
}

func BenchmarkFormatRate(b *testing.B) {
	for i := 0; i < b.N; i++ {
		FormatRate(50000)
	}
}

func BenchmarkFormatPercentage(b *testing.B) {
	for i := 0; i < b.N; i++ {
		FormatPercentage(50.5)
	}
}

func BenchmarkTruncateFilename(b *testing.B) {
	filename := "this_is_a_very_long_filename_that_needs_truncation.csv.gz"
	for i := 0; i < b.N; i++ {
		truncateFilename(filename, 30)
	}
}

func BenchmarkWriteProgressFile(b *testing.B) {
	tmpDir := b.TempDir()
	progressFile := filepath.Join(tmpDir, "progress.txt")
	m := NewMonitor(nil, 10, progressFile)

	progresses := []FileProgress{
		{Filename: "file1.csv.gz", RowsProcessed: 1000, TotalRows: 5000, Percentage: 20.0, Rate: 500},
		{Filename: "file2.csv.gz", RowsProcessed: 2000, TotalRows: 5000, Percentage: 40.0, Rate: 600},
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		m.WriteProgressFile(progresses)
	}
}

