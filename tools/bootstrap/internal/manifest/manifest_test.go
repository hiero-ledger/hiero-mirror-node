// SPDX-License-Identifier: Apache-2.0

package manifest

import (
	"os"
	"path/filepath"
	"testing"
)

func createTestManifest(t *testing.T, content string) string {
	t.Helper()
	tmpDir := t.TempDir()
	manifestPath := filepath.Join(tmpDir, "manifest.csv")
	if err := os.WriteFile(manifestPath, []byte(content), 0644); err != nil {
		t.Fatalf("Failed to create test manifest: %v", err)
	}
	return manifestPath
}

func TestLoad_ValidManifest(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
account_balance/account_balance_p2024_01.csv.gz,58149,1234567,abc123def456
transaction/transaction_p2023_10.csv.gz,1234567,98765432,def456abc789
schema.sql.gz,N/A,12345,hash123
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	if m.Count() != 3 {
		t.Errorf("Expected 3 entries, got %d", m.Count())
	}
}

func TestLoad_EntryValues(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
account_balance_p2024_01.csv.gz,58149,1234567,abc123def456
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	entry, ok := m.Get("account_balance_p2024_01.csv.gz")
	if !ok {
		t.Fatal("Entry not found")
	}

	if entry.RowCount != 58149 {
		t.Errorf("Expected RowCount=58149, got %d", entry.RowCount)
	}
	if entry.FileSize != 1234567 {
		t.Errorf("Expected FileSize=1234567, got %d", entry.FileSize)
	}
	if entry.Blake3Hash != "abc123def456" {
		t.Errorf("Expected Blake3Hash='abc123def456', got '%s'", entry.Blake3Hash)
	}
}

func TestLoad_NARowCount(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
schema.sql.gz,N/A,12345,hash123
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	entry, ok := m.Get("schema.sql.gz")
	if !ok {
		t.Fatal("Entry not found")
	}

	if entry.RowCount != -1 {
		t.Errorf("Expected RowCount=-1 for N/A, got %d", entry.RowCount)
	}
}

func TestLoad_EmptyRowCount(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
file.csv.gz,,12345,hash123
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	entry, ok := m.Get("file.csv.gz")
	if !ok {
		t.Fatal("Entry not found")
	}

	if entry.RowCount != -1 {
		t.Errorf("Expected RowCount=-1 for empty, got %d", entry.RowCount)
	}
}

func TestGet_WithPath(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
subdir/myfile.csv.gz,100,200,abc
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	// Should find by full path
	_, ok := m.Get("subdir/myfile.csv.gz")
	if !ok {
		t.Error("Should find entry by full path")
	}

	// Should find by basename
	_, ok = m.Get("myfile.csv.gz")
	if !ok {
		t.Error("Should find entry by basename")
	}
}

func TestGetByBasename(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
subdir/myfile.csv.gz,100,200,abc
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	// GetByBasename should work
	entry, ok := m.GetByBasename("myfile.csv.gz")
	if !ok {
		t.Error("GetByBasename should find entry")
	}
	if entry.RowCount != 100 {
		t.Errorf("Expected RowCount=100, got %d", entry.RowCount)
	}

	// Non-existent should return false
	_, ok = m.GetByBasename("nonexistent.csv.gz")
	if ok {
		t.Error("GetByBasename should not find nonexistent entry")
	}
}

func TestFullPath(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
subdir/myfile.csv.gz,100,200,abc
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	entry, _ := m.Get("myfile.csv.gz")
	fullPath := m.FullPath(entry)

	expected := "/data/subdir/myfile.csv.gz"
	if fullPath != expected {
		t.Errorf("Expected '%s', got '%s'", expected, fullPath)
	}
}

func TestDataDir(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
file.csv.gz,100,200,abc
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/my/data/dir")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	if m.DataDir() != "/my/data/dir" {
		t.Errorf("Expected DataDir='/my/data/dir', got '%s'", m.DataDir())
	}
}

func TestAllBasenames(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
file1.csv.gz,100,200,abc
file2.csv.gz,200,300,def
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	basenames := m.AllBasenames()
	if len(basenames) != 2 {
		t.Errorf("Expected 2 basenames, got %d", len(basenames))
	}

	// Check both files are present (order may vary)
	found1, found2 := false, false
	for _, name := range basenames {
		if name == "file1.csv.gz" {
			found1 = true
		}
		if name == "file2.csv.gz" {
			found2 = true
		}
	}
	if !found1 || !found2 {
		t.Error("AllBasenames should contain both files")
	}
}

func TestAllFiles(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
subdir/file1.csv.gz,100,200,abc
file2.csv.gz,200,300,def
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	files := m.AllFiles()
	if len(files) != 2 {
		t.Errorf("Expected 2 files, got %d", len(files))
	}
}

func TestTotalExpectedRows(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
file1.csv.gz,100,200,abc
file2.csv.gz,200,300,def
file3.csv.gz,N/A,400,ghi
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	total := m.TotalExpectedRows()
	if total != 300 {
		t.Errorf("Expected total rows=300, got %d", total)
	}
}

func TestTotalExpectedBytes(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
file1.csv.gz,100,200,abc
file2.csv.gz,200,300,def
file3.csv.gz,N/A,400,ghi
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	total := m.TotalExpectedBytes()
	if total != 900 {
		t.Errorf("Expected total bytes=900, got %d", total)
	}
}

func TestExtractTableName(t *testing.T) {
	tests := []struct {
		filename string
		expected string
	}{
		{"account_balance_p2024_01.csv.gz", "account_balance"},
		{"account_balance_p2024_01_atma.csv.gz", "account_balance"},
		{"flyway_schema_history.csv.gz", "flyway_schema_history"},
		{"transaction_p2023_10.csv.gz", "transaction"},
		{"crypto_transfer_p2023_11.csv.gz", "crypto_transfer"},
		{"subdir/account_balance_p2024_01.csv.gz", "account_balance"},
		{"schema.sql.gz", "schema.sql"},
		// Edge cases
		{"file_pXXXX_01.csv.gz", "file_pXXXX_01"}, // Non-digit after _p
		{"simple.csv", "simple"},                   // .csv without .gz
		{"simple", "simple"},                       // No extension
	}

	for _, tc := range tests {
		result := extractTableName(tc.filename)
		if result != tc.expected {
			t.Errorf("extractTableName(%q): expected %q, got %q", tc.filename, tc.expected, result)
		}
	}
}

func TestIsDigit(t *testing.T) {
	tests := []struct {
		c        byte
		expected bool
	}{
		{'0', true},
		{'9', true},
		{'5', true},
		{'a', false},
		{'Z', false},
		{' ', false},
		{'-', false},
	}

	for _, tc := range tests {
		result := isDigit(tc.c)
		if result != tc.expected {
			t.Errorf("isDigit(%q): expected %v, got %v", tc.c, tc.expected, result)
		}
	}
}

func TestFilterByTable(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
account_balance_p2024_01.csv.gz,100,200,abc
account_balance_p2024_02.csv.gz,150,250,def
transaction_p2023_10.csv.gz,200,300,ghi
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	entries := m.FilterByTable("account_balance")
	if len(entries) != 2 {
		t.Errorf("Expected 2 account_balance entries, got %d", len(entries))
	}

	entries = m.FilterByTable("transaction")
	if len(entries) != 1 {
		t.Errorf("Expected 1 transaction entry, got %d", len(entries))
	}

	entries = m.FilterByTable("nonexistent")
	if len(entries) != 0 {
		t.Errorf("Expected 0 nonexistent entries, got %d", len(entries))
	}
}

func TestLoad_MissingFile(t *testing.T) {
	_, err := Load("/nonexistent/manifest.csv", "/data")
	if err == nil {
		t.Error("Expected error for missing file")
	}
}

func TestLoad_EmptyManifest(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	if m.Count() != 0 {
		t.Errorf("Expected 0 entries for empty manifest, got %d", m.Count())
	}
}

func TestLoad_MalformedRows(t *testing.T) {
	content := `filename,row_count,file_size,blake3_hash
valid.csv.gz,100,200,abc
too_few_fields.csv.gz,100
invalid_size.csv.gz,100,notanumber,abc
,100,200,abc
`
	path := createTestManifest(t, content)

	m, err := Load(path, "/data")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	// Only the valid entry should be loaded
	if m.Count() != 1 {
		t.Errorf("Expected 1 valid entry, got %d", m.Count())
	}

	_, ok := m.Get("valid.csv.gz")
	if !ok {
		t.Error("Valid entry should be present")
	}
}

func TestNormalizeFilename(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"file.csv.gz", "file.csv.gz"},
		{"subdir/file.csv.gz", "file.csv.gz"},
		{"a/b/c/file.csv.gz", "file.csv.gz"},
		{"./file.csv.gz", "file.csv.gz"},
	}

	for _, tc := range tests {
		result := normalizeFilename(tc.input)
		if result != tc.expected {
			t.Errorf("normalizeFilename(%q): expected %q, got %q", tc.input, tc.expected, result)
		}
	}
}

func BenchmarkLoad(b *testing.B) {
	// Create a larger manifest for benchmarking
	var content string
	content = "filename,row_count,file_size,blake3_hash\n"
	for i := 0; i < 1000; i++ {
		content += "file_" + string(rune('0'+i%10)) + "_p2024_01.csv.gz,12345,67890,abcdef123456\n"
	}

	tmpDir := b.TempDir()
	manifestPath := filepath.Join(tmpDir, "manifest.csv")
	os.WriteFile(manifestPath, []byte(content), 0644)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		Load(manifestPath, "/data")
	}
}

func BenchmarkGet(b *testing.B) {
	content := `filename,row_count,file_size,blake3_hash
account_balance_p2024_01.csv.gz,58149,1234567,abc123def456
`
	tmpDir := b.TempDir()
	manifestPath := filepath.Join(tmpDir, "manifest.csv")
	os.WriteFile(manifestPath, []byte(content), 0644)

	m, _ := Load(manifestPath, "/data")

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		m.Get("account_balance_p2024_01.csv.gz")
	}
}

func BenchmarkFilterByTable(b *testing.B) {
	content := `filename,row_count,file_size,blake3_hash
account_balance_p2024_01.csv.gz,100,200,abc
account_balance_p2024_02.csv.gz,150,250,def
transaction_p2023_10.csv.gz,200,300,ghi
`
	tmpDir := b.TempDir()
	manifestPath := filepath.Join(tmpDir, "manifest.csv")
	os.WriteFile(manifestPath, []byte(content), 0644)

	m, _ := Load(manifestPath, "/data")

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		m.FilterByTable("account_balance")
	}
}

