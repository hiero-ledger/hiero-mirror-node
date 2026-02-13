package tracking

import (
	"os"
	"path/filepath"
	"sync"
	"testing"
)

func createTestTracker(t *testing.T) *Tracker {
	t.Helper()
	tmpDir := t.TempDir()
	trackingPath := filepath.Join(tmpDir, "tracking.txt")
	return NewTracker(trackingPath)
}

func TestReadStatus_NonExistentFile(t *testing.T) {
	tracker := createTestTracker(t)

	status, hashStatus, err := tracker.ReadStatus("somefile.csv.gz")
	if err != nil {
		t.Fatalf("Unexpected error: %v", err)
	}

	if status != StatusNotStarted {
		t.Errorf("Expected StatusNotStarted, got %s", status)
	}
	if hashStatus != HashUnverified {
		t.Errorf("Expected HashUnverified, got %s", hashStatus)
	}
}

func TestWriteAndReadStatus(t *testing.T) {
	tracker := createTestTracker(t)

	// Write status
	err := tracker.WriteStatus("file1.csv.gz", StatusInProgress, HashUnverified)
	if err != nil {
		t.Fatalf("WriteStatus failed: %v", err)
	}

	// Read it back
	status, hashStatus, err := tracker.ReadStatus("file1.csv.gz")
	if err != nil {
		t.Fatalf("ReadStatus failed: %v", err)
	}

	if status != StatusInProgress {
		t.Errorf("Expected StatusInProgress, got %s", status)
	}
	if hashStatus != HashUnverified {
		t.Errorf("Expected HashUnverified, got %s", hashStatus)
	}
}

func TestWriteStatus_UpdateExisting(t *testing.T) {
	tracker := createTestTracker(t)

	// Write initial status
	tracker.WriteStatus("file1.csv.gz", StatusInProgress, HashUnverified)

	// Update status
	err := tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	if err != nil {
		t.Fatalf("WriteStatus update failed: %v", err)
	}

	// Verify update
	status, hashStatus, err := tracker.ReadStatus("file1.csv.gz")
	if err != nil {
		t.Fatalf("ReadStatus failed: %v", err)
	}

	if status != StatusImported {
		t.Errorf("Expected StatusImported, got %s", status)
	}
	if hashStatus != HashVerified {
		t.Errorf("Expected HashVerified, got %s", hashStatus)
	}
}

func TestWriteStatus_PreservesOtherEntries(t *testing.T) {
	tracker := createTestTracker(t)

	// Write multiple entries
	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusInProgress, HashUnverified)
	tracker.WriteStatus("file3.csv.gz", StatusFailedToImport, HashVerified)

	// Update one entry
	tracker.WriteStatus("file2.csv.gz", StatusImported, HashVerified)

	// Verify all entries are preserved
	status1, _, _ := tracker.ReadStatus("file1.csv.gz")
	status2, _, _ := tracker.ReadStatus("file2.csv.gz")
	status3, _, _ := tracker.ReadStatus("file3.csv.gz")

	if status1 != StatusImported {
		t.Errorf("file1: Expected StatusImported, got %s", status1)
	}
	if status2 != StatusImported {
		t.Errorf("file2: Expected StatusImported, got %s", status2)
	}
	if status3 != StatusFailedToImport {
		t.Errorf("file3: Expected StatusFailedToImport, got %s", status3)
	}
}

func TestGetAllStatuses(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusInProgress, HashUnverified)

	statuses, err := tracker.GetAllStatuses()
	if err != nil {
		t.Fatalf("GetAllStatuses failed: %v", err)
	}

	if len(statuses) != 2 {
		t.Errorf("Expected 2 statuses, got %d", len(statuses))
	}

	if statuses["file1.csv.gz"].Status != StatusImported {
		t.Errorf("file1: Expected StatusImported, got %s", statuses["file1.csv.gz"].Status)
	}
}

func TestGetAllStatuses_EmptyFile(t *testing.T) {
	tracker := createTestTracker(t)

	statuses, err := tracker.GetAllStatuses()
	if err != nil {
		t.Fatalf("GetAllStatuses failed: %v", err)
	}

	if len(statuses) != 0 {
		t.Errorf("Expected 0 statuses, got %d", len(statuses))
	}
}

func TestGetFilesNotImported(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusInProgress, HashUnverified)
	tracker.WriteStatus("file3.csv.gz", StatusFailedToImport, HashVerified)

	files, err := tracker.GetFilesNotImported()
	if err != nil {
		t.Fatalf("GetFilesNotImported failed: %v", err)
	}

	if len(files) != 2 {
		t.Errorf("Expected 2 files not imported, got %d", len(files))
	}
}

func TestGetFilesWithStatus(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file3.csv.gz", StatusInProgress, HashUnverified)
	tracker.WriteStatus("file4.csv.gz", StatusFailedToImport, HashVerified)

	// Test StatusImported
	files, err := tracker.GetFilesWithStatus(StatusImported)
	if err != nil {
		t.Fatalf("GetFilesWithStatus failed: %v", err)
	}
	if len(files) != 2 {
		t.Errorf("Expected 2 imported files, got %d", len(files))
	}

	// Test StatusInProgress
	files, err = tracker.GetFilesWithStatus(StatusInProgress)
	if err != nil {
		t.Fatalf("GetFilesWithStatus failed: %v", err)
	}
	if len(files) != 1 {
		t.Errorf("Expected 1 in-progress file, got %d", len(files))
	}

	// Test StatusFailedToImport
	files, err = tracker.GetFilesWithStatus(StatusFailedToImport)
	if err != nil {
		t.Fatalf("GetFilesWithStatus failed: %v", err)
	}
	if len(files) != 1 {
		t.Errorf("Expected 1 failed file, got %d", len(files))
	}

	// Test nonexistent status
	files, err = tracker.GetFilesWithStatus(StatusNotStarted)
	if err != nil {
		t.Fatalf("GetFilesWithStatus failed: %v", err)
	}
	if len(files) != 0 {
		t.Errorf("Expected 0 not-started files, got %d", len(files))
	}
}

func TestCountByStatus(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file3.csv.gz", StatusInProgress, HashUnverified)
	tracker.WriteStatus("file4.csv.gz", StatusFailedToImport, HashVerified)

	counts, err := tracker.CountByStatus()
	if err != nil {
		t.Fatalf("CountByStatus failed: %v", err)
	}

	if counts[StatusImported] != 2 {
		t.Errorf("Expected 2 imported, got %d", counts[StatusImported])
	}
	if counts[StatusInProgress] != 1 {
		t.Errorf("Expected 1 in progress, got %d", counts[StatusInProgress])
	}
	if counts[StatusFailedToImport] != 1 {
		t.Errorf("Expected 1 failed, got %d", counts[StatusFailedToImport])
	}
}

func TestIsImported(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusInProgress, HashUnverified)

	imported1, _ := tracker.IsImported("file1.csv.gz")
	imported2, _ := tracker.IsImported("file2.csv.gz")
	imported3, _ := tracker.IsImported("file3.csv.gz") // not in tracking

	if !imported1 {
		t.Error("file1 should be imported")
	}
	if imported2 {
		t.Error("file2 should not be imported")
	}
	if imported3 {
		t.Error("file3 (nonexistent) should not be imported")
	}
}

func TestNeedsImport(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)
	tracker.WriteStatus("file2.csv.gz", StatusInProgress, HashUnverified)

	needs1, _ := tracker.NeedsImport("file1.csv.gz")
	needs2, _ := tracker.NeedsImport("file2.csv.gz")
	needs3, _ := tracker.NeedsImport("file3.csv.gz") // not in tracking

	if needs1 {
		t.Error("file1 should not need import")
	}
	if !needs2 {
		t.Error("file2 should need import")
	}
	if !needs3 {
		t.Error("file3 (nonexistent) should need import")
	}
}

func TestReadStatus_WithPath(t *testing.T) {
	tracker := createTestTracker(t)

	// Write with basename
	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)

	// Read with full path - should still work
	status, _, err := tracker.ReadStatus("/path/to/file1.csv.gz")
	if err != nil {
		t.Fatalf("ReadStatus failed: %v", err)
	}

	if status != StatusImported {
		t.Errorf("Expected StatusImported, got %s", status)
	}
}

func TestPath(t *testing.T) {
	tmpDir := t.TempDir()
	trackingPath := filepath.Join(tmpDir, "my_tracking.txt")
	tracker := NewTracker(trackingPath)

	if tracker.Path() != trackingPath {
		t.Errorf("Path(): expected %s, got %s", trackingPath, tracker.Path())
	}
}

func TestConcurrentReadWrite(t *testing.T) {
	tracker := createTestTracker(t)

	var wg sync.WaitGroup
	iterations := 100
	goroutines := 10

	// Start multiple readers and writers
	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			filename := "file" + string(rune('0'+id%10)) + ".csv.gz"

			for i := 0; i < iterations; i++ {
				// Write
				err := tracker.WriteStatus(filename, StatusInProgress, HashUnverified)
				if err != nil {
					t.Errorf("WriteStatus failed: %v", err)
					return
				}

				// Read
				_, _, err = tracker.ReadStatus(filename)
				if err != nil {
					t.Errorf("ReadStatus failed: %v", err)
					return
				}
			}
		}(g)
	}

	wg.Wait()
}

func TestClear(t *testing.T) {
	tracker := createTestTracker(t)

	tracker.WriteStatus("file1.csv.gz", StatusImported, HashVerified)

	err := tracker.Clear()
	if err != nil {
		t.Fatalf("Clear failed: %v", err)
	}

	// File should not exist
	_, err = os.Stat(tracker.Path())
	if !os.IsNotExist(err) {
		t.Error("Tracking file should not exist after Clear")
	}
}

func TestClear_NonExistentFile(t *testing.T) {
	tracker := createTestTracker(t)

	// Clear on non-existent file should return error (or nil)
	err := tracker.Clear()
	// It's ok if it returns an error or nil for non-existent file
	_ = err
}

func TestStatusConstants(t *testing.T) {
	// Verify status constants have expected values
	if StatusNotStarted != "NOT_STARTED" {
		t.Errorf("StatusNotStarted: expected NOT_STARTED, got %s", StatusNotStarted)
	}
	if StatusInProgress != "IN_PROGRESS" {
		t.Errorf("StatusInProgress: expected IN_PROGRESS, got %s", StatusInProgress)
	}
	if StatusImported != "IMPORTED" {
		t.Errorf("StatusImported: expected IMPORTED, got %s", StatusImported)
	}
	if StatusFailedValidation != "FAILED_VALIDATION" {
		t.Errorf("StatusFailedValidation: expected FAILED_VALIDATION, got %s", StatusFailedValidation)
	}
	if StatusFailedToImport != "FAILED_TO_IMPORT" {
		t.Errorf("StatusFailedToImport: expected FAILED_TO_IMPORT, got %s", StatusFailedToImport)
	}
}

func TestHashStatusConstants(t *testing.T) {
	if HashUnverified != "HASH_UNVERIFIED" {
		t.Errorf("HashUnverified: expected HASH_UNVERIFIED, got %s", HashUnverified)
	}
	if HashVerified != "HASH_VERIFIED" {
		t.Errorf("HashVerified: expected HASH_VERIFIED, got %s", HashVerified)
	}
}

func TestFileStatus_Struct(t *testing.T) {
	fs := FileStatus{
		Status:     StatusImported,
		HashStatus: HashVerified,
	}

	if fs.Status != StatusImported {
		t.Error("FileStatus.Status not set correctly")
	}
	if fs.HashStatus != HashVerified {
		t.Error("FileStatus.HashStatus not set correctly")
	}
}

func BenchmarkWriteStatus(b *testing.B) {
	tmpDir := b.TempDir()
	trackingPath := filepath.Join(tmpDir, "tracking.txt")
	tracker := NewTracker(trackingPath)

	// Pre-populate with some entries
	for i := 0; i < 100; i++ {
		tracker.WriteStatus("existing_file_"+string(rune('0'+i%10))+".csv.gz", StatusImported, HashVerified)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		tracker.WriteStatus("benchmark_file.csv.gz", StatusInProgress, HashUnverified)
	}
}

func BenchmarkReadStatus(b *testing.B) {
	tmpDir := b.TempDir()
	trackingPath := filepath.Join(tmpDir, "tracking.txt")
	tracker := NewTracker(trackingPath)

	// Pre-populate with entries
	for i := 0; i < 100; i++ {
		tracker.WriteStatus("file_"+string(rune('0'+i%10))+".csv.gz", StatusImported, HashVerified)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		tracker.ReadStatus("file_5.csv.gz")
	}
}

func BenchmarkGetAllStatuses(b *testing.B) {
	tmpDir := b.TempDir()
	trackingPath := filepath.Join(tmpDir, "tracking.txt")
	tracker := NewTracker(trackingPath)

	// Pre-populate with entries
	for i := 0; i < 100; i++ {
		tracker.WriteStatus("file_"+string(rune('0'+i%10))+".csv.gz", StatusImported, HashVerified)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		tracker.GetAllStatuses()
	}
}

