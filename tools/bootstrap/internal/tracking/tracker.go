// Package tracking provides JSON-based import status tracking for resumable operations.
package tracking

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

// Status represents the import status of a file.
type Status string

const (
	StatusNotStarted       Status = "NOT_STARTED"
	StatusInProgress       Status = "IN_PROGRESS"
	StatusImported         Status = "IMPORTED"
	StatusFailedValidation Status = "FAILED_VALIDATION"
	StatusFailedToImport   Status = "FAILED_TO_IMPORT"
)

// HashStatus represents the hash verification status of a file.
type HashStatus string

const (
	HashUnverified HashStatus = "HASH_UNVERIFIED"
	HashVerified   HashStatus = "HASH_VERIFIED"
)

// FileStatus holds the complete status information for a single file.
type FileStatus struct {
	Status     Status     `json:"status"`
	HashStatus HashStatus `json:"hash_status"`
}

// TrackingData is the complete JSON structure.
type TrackingData map[string]FileStatus

// Tracker manages import status tracking with JSON file storage.
type Tracker struct {
	path  string
	mu    sync.RWMutex
	data  TrackingData
}

// NewTracker creates a new Tracker for the given tracking file path.
func NewTracker(path string) *Tracker {
	return &Tracker{
		path: path,
		data: make(TrackingData),
	}
}

// Open loads existing tracking data from the JSON file.
func (t *Tracker) Open() error {
	t.mu.Lock()
	defer t.mu.Unlock()

	content, err := os.ReadFile(t.path)
	if os.IsNotExist(err) {
		t.data = make(TrackingData)
		return nil
	}
	if err != nil {
		return fmt.Errorf("failed to read tracking file: %w", err)
	}

	if err := json.Unmarshal(content, &t.data); err != nil {
		return fmt.Errorf("failed to parse tracking file: %w", err)
	}

	return nil
}

// Close is a no-op for JSON tracking (data is written on each update).
func (t *Tracker) Close() error {
	return nil
}

// save writes the current tracking data to the JSON file atomically.
func (t *Tracker) save() error {
	content, err := json.MarshalIndent(t.data, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal tracking data: %w", err)
	}

	// Write to temp file and rename for atomic update
	tmpPath := t.path + ".tmp"
	if err := os.WriteFile(tmpPath, content, 0644); err != nil {
		return fmt.Errorf("failed to write temp file: %w", err)
	}

	if err := os.Rename(tmpPath, t.path); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to rename temp file: %w", err)
	}

	return nil
}

// ReadStatus returns the current status of a file.
func (t *Tracker) ReadStatus(filename string) (Status, HashStatus, error) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	basename := filepath.Base(filename)

	if fs, ok := t.data[basename]; ok {
		return fs.Status, fs.HashStatus, nil
	}

	return StatusNotStarted, HashUnverified, nil
}

// WriteStatus updates the status of a file and saves to JSON.
func (t *Tracker) WriteStatus(filename string, status Status, hashStatus HashStatus) error {
	t.mu.Lock()
	defer t.mu.Unlock()

	basename := filepath.Base(filename)

	t.data[basename] = FileStatus{
		Status:     status,
		HashStatus: hashStatus,
	}

	return t.save()
}

// GetAllStatuses returns all file statuses.
func (t *Tracker) GetAllStatuses() (map[string]FileStatus, error) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	// Return a copy to avoid concurrent access issues
	result := make(map[string]FileStatus)
	for k, v := range t.data {
		result[k] = v
	}
	return result, nil
}

// GetFilesNotImported returns all files that are not in IMPORTED status.
func (t *Tracker) GetFilesNotImported() ([]string, error) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	var result []string
	for filename, fs := range t.data {
		if fs.Status != StatusImported {
			result = append(result, filename)
		}
	}
	return result, nil
}

// GetFilesWithStatus returns all files with a specific status.
func (t *Tracker) GetFilesWithStatus(status Status) ([]string, error) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	var result []string
	for filename, fs := range t.data {
		if fs.Status == status {
			result = append(result, filename)
		}
	}
	return result, nil
}

// CountByStatus returns the count of files for each status.
func (t *Tracker) CountByStatus() (map[Status]int, error) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	counts := make(map[Status]int)
	for _, fs := range t.data {
		counts[fs.Status]++
	}
	return counts, nil
}

// IsImported returns true if the file has been successfully imported.
func (t *Tracker) IsImported(filename string) (bool, error) {
	status, _, err := t.ReadStatus(filename)
	if err != nil {
		return false, err
	}
	return status == StatusImported, nil
}

// NeedsImport returns true if the file needs to be imported (not IMPORTED status).
func (t *Tracker) NeedsImport(filename string) (bool, error) {
	status, _, err := t.ReadStatus(filename)
	if err != nil {
		return false, err
	}
	return status != StatusImported, nil
}

// Clear removes all entries from the tracking file.
func (t *Tracker) Clear() error {
	t.mu.Lock()
	defer t.mu.Unlock()

	t.data = make(TrackingData)
	return os.Remove(t.path)
}

// Path returns the path to the tracking file.
func (t *Tracker) Path() string {
	return t.path
}

// TotalFiles returns the total number of tracked files.
func (t *Tracker) TotalFiles() int {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return len(t.data)
}
