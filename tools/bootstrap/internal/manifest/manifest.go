// Package manifest handles parsing and querying the CSV manifest file
// that contains expected file sizes, row counts, and BLAKE3 hashes.
package manifest

import (
	"encoding/csv"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// Entry represents a single file in the manifest.
type Entry struct {
	Filename   string // Relative path from data directory
	RowCount   int64  // Expected row count (-1 if N/A)
	FileSize   int64  // Expected file size in bytes
	Blake3Hash string // Expected BLAKE3 hash (hex string)
}

// Manifest holds all entries from the manifest file.
type Manifest struct {
	entries map[string]Entry // keyed by normalized filename
	dataDir string           // base directory for files
}

// Load parses a manifest CSV file and returns a Manifest.
// Expected CSV format: filename,row_count,file_size,blake3_hash
func Load(manifestPath, dataDir string) (*Manifest, error) {
	file, err := os.Open(manifestPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open manifest: %w", err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	reader.FieldsPerRecord = -1 // Allow variable field count
	reader.TrimLeadingSpace = true

	records, err := reader.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("failed to parse manifest CSV: %w", err)
	}

	m := &Manifest{
		entries: make(map[string]Entry),
		dataDir: dataDir,
	}

	for i, record := range records {
		if i == 0 {
			continue
		}
		if len(record) < 4 {
			continue
		}

		filename := strings.TrimSpace(record[0])
		if filename == "" {
			continue
		}

		// Parse row count (may be "N/A")
		var rowCount int64 = -1
		if record[1] != "N/A" && record[1] != "" {
			rowCount, _ = strconv.ParseInt(strings.TrimSpace(record[1]), 10, 64)
		}

		// Parse file size
		fileSize, err := strconv.ParseInt(strings.TrimSpace(record[2]), 10, 64)
		if err != nil {
			continue // Skip if file size is invalid
		}

		blake3Hash := strings.TrimSpace(record[3])

		// Normalize filename (remove leading path components if present)
		normalizedName := normalizeFilename(filename)

		m.entries[normalizedName] = Entry{
			Filename:   filename,
			RowCount:   rowCount,
			FileSize:   fileSize,
			Blake3Hash: blake3Hash,
		}
	}

	return m, nil
}

// normalizeFilename extracts the base filename for consistent lookups.
// Handles both "subdir/file.csv.gz" and "file.csv.gz" formats.
func normalizeFilename(filename string) string {
	return filepath.Base(filename)
}

// Get returns the entry for a given filename.
// The filename can be a full path or just the base name.
func (m *Manifest) Get(filename string) (Entry, bool) {
	normalized := normalizeFilename(filename)
	e, ok := m.entries[normalized]
	return e, ok
}

// GetByBasename returns the entry for a given base filename.
func (m *Manifest) GetByBasename(basename string) (Entry, bool) {
	e, ok := m.entries[basename]
	return e, ok
}

// AllFiles returns all filenames in the manifest.
func (m *Manifest) AllFiles() []string {
	files := make([]string, 0, len(m.entries))
	for _, e := range m.entries {
		files = append(files, e.Filename)
	}
	return files
}

// AllBasenames returns all base filenames in the manifest.
func (m *Manifest) AllBasenames() []string {
	names := make([]string, 0, len(m.entries))
	for name := range m.entries {
		names = append(names, name)
	}
	return names
}

// Count returns the number of entries in the manifest.
func (m *Manifest) Count() int {
	return len(m.entries)
}

// DataDir returns the base data directory.
func (m *Manifest) DataDir() string {
	return m.dataDir
}

// FullPath returns the full path to a file given its entry.
func (m *Manifest) FullPath(e Entry) string {
	return filepath.Join(m.dataDir, e.Filename)
}

// TotalExpectedRows returns the sum of all expected row counts.
// Files with RowCount=-1 (N/A) are excluded.
func (m *Manifest) TotalExpectedRows() int64 {
	var total int64
	for _, e := range m.entries {
		if e.RowCount > 0 {
			total += e.RowCount
		}
	}
	return total
}

// TotalExpectedBytes returns the sum of all expected file sizes.
func (m *Manifest) TotalExpectedBytes() int64 {
	var total int64
	for _, e := range m.entries {
		total += e.FileSize
	}
	return total
}

// FilterByTable returns entries that belong to a specific table.
// Table name is extracted from the filename pattern: {table}_pYYYY_MM.csv.gz or {table}.csv.gz
func (m *Manifest) FilterByTable(tableName string) []Entry {
	var result []Entry
	for _, e := range m.entries {
		if extractTableName(e.Filename) == tableName {
			result = append(result, e)
		}
	}
	return result
}

// extractTableName extracts the base table name from a filename.
// Examples:
//   - "account_balance_p2024_01.csv.gz" -> "account_balance"
//   - "flyway_schema_history.csv.gz" -> "flyway_schema_history"
func extractTableName(filename string) string {
	base := filepath.Base(filename)
	base = strings.TrimSuffix(base, ".csv.gz")
	base = strings.TrimSuffix(base, ".gz")
	base = strings.TrimSuffix(base, ".csv")

	// Check for partition pattern: _pYYYY_MM or _pYYYY_MM_atma
	if idx := strings.Index(base, "_p"); idx > 0 {
		// Verify it's a partition pattern (digit follows)
		suffix := base[idx+2:]
		if len(suffix) >= 4 && isDigit(suffix[0]) {
			return base[:idx]
		}
	}

	return base
}

func isDigit(c byte) bool {
	return c >= '0' && c <= '9'
}
