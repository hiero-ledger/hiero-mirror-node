// SPDX-License-Identifier: Apache-2.0

package importer

import (
	"bytes"
	"io"
	"testing"
)

func TestGetTableName(t *testing.T) {
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
		{"contract_action_p2022_06.csv.gz", "contract_action"},
		{"contract_result_p2022_06_atma.csv.gz", "contract_result"},
		{"topic_message_p2024_01.csv.gz", "topic_message"},
		// Non-partitioned tables
		{"address_book_entry.csv.gz", "address_book_entry"},
		{"entity.csv.gz", "entity"},
		// Edge cases
		{"simple.csv", "simple"},
		{"simple.gz", "simple"},
		{"nested/path/file_p2024_01.csv.gz", "file"},
	}

	for _, tc := range tests {
		result := GetTableName(tc.filename)
		if result != tc.expected {
			t.Errorf("GetTableName(%q): expected %q, got %q", tc.filename, tc.expected, result)
		}
	}
}

func TestGetTableOrPartition(t *testing.T) {
	tests := []struct {
		filename string
		expected string
	}{
		{"account_balance_p2024_01.csv.gz", "account_balance_p2024_01"},
		{"account_balance_p2024_01_atma.csv.gz", "account_balance_p2024_01_atma"},
		{"flyway_schema_history.csv.gz", "flyway_schema_history"},
		{"subdir/transaction_p2023_10.csv.gz", "transaction_p2023_10"},
		{"simple.csv", "simple"},
	}

	for _, tc := range tests {
		result := GetTableOrPartition(tc.filename)
		if result != tc.expected {
			t.Errorf("GetTableOrPartition(%q): expected %q, got %q", tc.filename, tc.expected, result)
		}
	}
}

func TestIsPartitioned(t *testing.T) {
	tests := []struct {
		filename    string
		partitioned bool
	}{
		{"account_balance_p2024_01.csv.gz", true},
		{"account_balance_p2024_01_atma.csv.gz", true},
		{"flyway_schema_history.csv.gz", false},
		{"entity.csv.gz", false},
		{"transaction_p2023_10.csv.gz", true},
		// Edge cases - invalid partition patterns
		{"file_p2024.csv.gz", false},        // Missing month
		{"file_p24_01.csv.gz", false},       // Short year
		{"file_pXXXX_01.csv.gz", false},     // Non-numeric year
		{"file_p2024_13.csv.gz", true},      // Month 13 (still matches pattern)
		{"token_balance_p2019_01.csv.gz", true},
	}

	for _, tc := range tests {
		result := IsPartitioned(tc.filename)
		if result != tc.partitioned {
			t.Errorf("IsPartitioned(%q): expected %v, got %v", tc.filename, tc.partitioned, result)
		}
	}
}

func TestIsSpecialFile(t *testing.T) {
	tests := []struct {
		filename string
		special  bool
	}{
		{"schema.sql.gz", true},
		{"MIRRORNODE_VERSION.gz", true},
		{"transaction_p2023_10.csv.gz", false},
		{"entity.csv.gz", false},
		{"subdir/schema.sql.gz", true},
		{"subdir/MIRRORNODE_VERSION.gz", true},
		// Case sensitive
		{"Schema.sql.gz", false},
		{"mirrornode_version.gz", false},
	}

	for _, tc := range tests {
		result := IsSpecialFile(tc.filename)
		if result != tc.special {
			t.Errorf("IsSpecialFile(%q): expected %v, got %v", tc.filename, tc.special, result)
		}
	}
}

func TestParseHeaderToColumns(t *testing.T) {
	tests := []struct {
		header   string
		expected string
	}{
		{"col1,col2,col3\n", `"col1","col2","col3"`},
		{"id,name,value\r\n", `"id","name","value"`},
		{"single\n", `"single"`},
		{"col_with_underscore,another_one\n", `"col_with_underscore","another_one"`},
		// Edge cases
		{"a,b,c", `"a","b","c"`},   // No newline
		{"\n", `""`},               // Just newline
		{"", `""`},                 // Empty
		{"col1\r", `"col1"`},       // Just CR
		{"a,b,c\r\n", `"a","b","c"`}, // CRLF
	}

	for _, tc := range tests {
		result := parseHeaderToColumns([]byte(tc.header))
		if result != tc.expected {
			t.Errorf("parseHeaderToColumns(%q): expected %q, got %q", tc.header, tc.expected, result)
		}
	}
}

func TestTrimRight(t *testing.T) {
	tests := []struct {
		input    []byte
		chars    []byte
		expected []byte
	}{
		{[]byte("hello\n"), []byte{'\n'}, []byte("hello")},
		{[]byte("hello\r\n"), []byte{'\r', '\n'}, []byte("hello")},
		{[]byte("hello"), []byte{'\n'}, []byte("hello")},
		{[]byte("\n\n\n"), []byte{'\n'}, []byte("")},
		{[]byte(""), []byte{'\n'}, []byte("")},
		{[]byte("abc  "), []byte{' '}, []byte("abc")},
	}

	for _, tc := range tests {
		result := trimRight(tc.input, tc.chars...)
		if !bytes.Equal(result, tc.expected) {
			t.Errorf("trimRight(%q, %v): expected %q, got %q", tc.input, tc.chars, tc.expected, result)
		}
	}
}

func TestImportResult_Struct(t *testing.T) {
	result := ImportResult{
		RowsImported: 1000,
		BytesRead:    50000,
		TableName:    "account_balance",
		Error:        nil,
	}

	if result.RowsImported != 1000 {
		t.Error("RowsImported not set correctly")
	}
	if result.BytesRead != 50000 {
		t.Error("BytesRead not set correctly")
	}
	if result.TableName != "account_balance" {
		t.Error("TableName not set correctly")
	}
	if result.Error != nil {
		t.Error("Error should be nil")
	}
}

func TestImportResult_WithError(t *testing.T) {
	err := io.EOF
	result := ImportResult{
		Error: err,
	}

	if result.Error != err {
		t.Error("Error not set correctly")
	}
}

func TestStreamingReader(t *testing.T) {
	data := []byte("test data for streaming")
	reader := NewStreamingReader(&mockReader{data: data})

	buf := make([]byte, 10)
	n1, _ := reader.Read(buf)
	n2, _ := reader.Read(buf)
	n3, _ := reader.Read(buf)

	total := reader.BytesRead()
	expected := int64(n1 + n2 + n3)
	if total != expected {
		t.Errorf("Expected BytesRead=%d, got %d", expected, total)
	}
}

func TestStreamingReader_Empty(t *testing.T) {
	reader := NewStreamingReader(&mockReader{data: []byte{}})

	buf := make([]byte, 10)
	n, _ := reader.Read(buf)

	if n != 0 {
		t.Errorf("Expected 0 bytes read, got %d", n)
	}
	if reader.BytesRead() != 0 {
		t.Errorf("Expected BytesRead=0, got %d", reader.BytesRead())
	}
}

func TestNewStreamingReader(t *testing.T) {
	r := bytes.NewReader([]byte("test"))
	sr := NewStreamingReader(r)

	if sr == nil {
		t.Fatal("NewStreamingReader returned nil")
	}
	if sr.reader == nil {
		t.Error("reader should be set")
	}
	if sr.bytesRead != 0 {
		t.Error("bytesRead should be 0")
	}
}

type mockReader struct {
	data []byte
	pos  int
}

func (m *mockReader) Read(p []byte) (int, error) {
	if m.pos >= len(m.data) {
		return 0, nil
	}
	n := copy(p, m.data[m.pos:])
	m.pos += n
	return n, nil
}

func BenchmarkGetTableName(b *testing.B) {
	filename := "account_balance_p2024_01.csv.gz"
	for i := 0; i < b.N; i++ {
		GetTableName(filename)
	}
}

func BenchmarkGetTableOrPartition(b *testing.B) {
	filename := "account_balance_p2024_01.csv.gz"
	for i := 0; i < b.N; i++ {
		GetTableOrPartition(filename)
	}
}

func BenchmarkParseHeaderToColumns(b *testing.B) {
	header := []byte("consensus_timestamp,payer_account_id,amount,node_account_id,type,result,scheduled\n")
	for i := 0; i < b.N; i++ {
		parseHeaderToColumns(header)
	}
}

func BenchmarkIsPartitioned(b *testing.B) {
	filename := "account_balance_p2024_01.csv.gz"
	for i := 0; i < b.N; i++ {
		IsPartitioned(filename)
	}
}

func BenchmarkIsSpecialFile(b *testing.B) {
	filename := "schema.sql.gz"
	for i := 0; i < b.N; i++ {
		IsSpecialFile(filename)
	}
}

func BenchmarkTrimRight(b *testing.B) {
	data := []byte("hello world\r\n")
	for i := 0; i < b.N; i++ {
		trimRight(data, '\r', '\n')
	}
}

