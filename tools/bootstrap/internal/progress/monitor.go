// SPDX-License-Identifier: Apache-2.0

// Package progress provides real-time import progress monitoring.
// Queries pg_stat_progress_copy and calculates processing rates.
package progress

import (
	"context"
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/jackc/pgx/v5"
	"golang.org/x/text/language"
	"golang.org/x/text/message"
)

// FileProgress holds progress info for a single file import.
type FileProgress struct {
	Filename      string
	RowsProcessed int64
	TotalRows     int64
	Percentage    float64
	Rate          int64 // rows per second
}

// Monitor tracks import progress and calculates rates.
type Monitor struct {
	conn        *pgx.Conn
	connMu      sync.Mutex // serializes all conn.Exec/Query calls
	interval    time.Duration
	dbTable     string // temporary progress table name
	mu          sync.Mutex
	lastState   map[string]progressState
	lastUpdate  time.Time
	progressFile string
	printer     *message.Printer
}

type progressState struct {
	rows int64
	time time.Time
}

// NewMonitor creates a new progress monitor.
func NewMonitor(conn *pgx.Conn, interval time.Duration, progressFile string) *Monitor {
	return &Monitor{
		conn:        conn,
		interval:    interval,
		dbTable:     "bootstrap_manifest_progress",
		lastState:   make(map[string]progressState),
		progressFile: progressFile,
		printer:     message.NewPrinter(language.English),
	}
}

// CreateProgressTable creates the temporary progress tracking table.
func (m *Monitor) CreateProgressTable(ctx context.Context) error {
	query := fmt.Sprintf(`
		CREATE TABLE IF NOT EXISTS %s (
			filename TEXT PRIMARY KEY,
			total_rows BIGINT,
			status TEXT DEFAULT 'pending'
		)
	`, m.dbTable)

	m.connMu.Lock()
	_, err := m.conn.Exec(ctx, query)
	m.connMu.Unlock()
	return err
}

// RegisterFile registers a file for progress tracking.
func (m *Monitor) RegisterFile(ctx context.Context, filename string, totalRows int64) error {
	query := fmt.Sprintf(`
		INSERT INTO %s (filename, total_rows, status)
		VALUES ($1, $2, 'importing')
		ON CONFLICT (filename) DO UPDATE SET total_rows = $2, status = 'importing'
	`, m.dbTable)

	m.connMu.Lock()
	_, err := m.conn.Exec(ctx, query, filename, totalRows)
	m.connMu.Unlock()
	return err
}

// MarkComplete marks a file as completed.
func (m *Monitor) MarkComplete(ctx context.Context, filename string) error {
	query := fmt.Sprintf(`
		UPDATE %s SET status = 'complete' WHERE filename = $1
	`, m.dbTable)

	m.connMu.Lock()
	_, err := m.conn.Exec(ctx, query, filename)
	m.connMu.Unlock()
	return err
}

// FetchProgress queries current import progress from PostgreSQL.
func (m *Monitor) FetchProgress(ctx context.Context) ([]FileProgress, error) {
	// Query pg_stat_progress_copy joined with our tracking table
	query := fmt.Sprintf(`
		SELECT 
			COALESCE(regexp_replace(a.application_name, '^bootstrap_copy_', ''), 'unknown') AS filename,
			COALESCE(p.tuples_processed, 0) AS rows_processed,
			COALESCE(t.total_rows, 0) AS total_rows
		FROM pg_stat_progress_copy p
		JOIN pg_stat_activity a ON a.pid = p.pid
		LEFT JOIN %s t ON t.filename = regexp_replace(a.application_name, '^bootstrap_copy_', '')
		WHERE a.application_name LIKE 'bootstrap_copy_%%'
	`, m.dbTable)

	m.connMu.Lock()
	rows, err := m.conn.Query(ctx, query)
	if err != nil {
		m.connMu.Unlock()
		return nil, err
	}
	defer rows.Close()
	// connMu released after rows are fully consumed
	defer m.connMu.Unlock()

	m.mu.Lock()
	defer m.mu.Unlock()

	now := time.Now()
	var results []FileProgress

	for rows.Next() {
		var fp FileProgress
		if err := rows.Scan(&fp.Filename, &fp.RowsProcessed, &fp.TotalRows); err != nil {
			continue
		}

		// Calculate rate
		if prev, ok := m.lastState[fp.Filename]; ok {
			elapsed := now.Sub(prev.time).Seconds()
			if elapsed > 0 {
				fp.Rate = int64(float64(fp.RowsProcessed-prev.rows) / elapsed)
			}
		}

		// Calculate percentage
		if fp.TotalRows > 0 {
			fp.Percentage = float64(fp.RowsProcessed) / float64(fp.TotalRows) * 100
		}

		// Update state
		m.lastState[fp.Filename] = progressState{
			rows: fp.RowsProcessed,
			time: now,
		}

		results = append(results, fp)
	}

	return results, rows.Err()
}

// WriteProgressFile writes progress to the output file in the expected format.
// Format: Filename Rows_Processed Total_Rows Percentage Rate
func (m *Monitor) WriteProgressFile(progresses []FileProgress) error {
	if m.progressFile == "" {
		return nil
	}

	file, err := os.Create(m.progressFile)
	if err != nil {
		return err
	}
	defer file.Close()

	// Write header
	fmt.Fprintf(file, "%-60s %20s %20s %10s %15s\n",
		"Filename", "Rows_Processed", "Total_Rows", "Percentage", "Rate")
	fmt.Fprintf(file, "%s\n", "------------------------------------------------------------------------------------------------------------------------------------------------")

	for _, p := range progresses {
		percentage := fmt.Sprintf("%.2f%%", p.Percentage)
		rate := m.printer.Sprintf("%d/s", p.Rate)
		rowsProcessed := m.printer.Sprintf("%d", p.RowsProcessed)
		totalRows := m.printer.Sprintf("%d", p.TotalRows)

		fmt.Fprintf(file, "%-60s %20s %20s %10s %15s\n",
			truncateFilename(p.Filename, 60),
			rowsProcessed,
			totalRows,
			percentage,
			rate)
	}

	return nil
}

// Run starts the progress monitoring loop.
// Runs until context is cancelled.
func (m *Monitor) Run(ctx context.Context) error {
	ticker := time.NewTicker(m.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			progresses, err := m.FetchProgress(ctx)
			if err != nil {
				continue // Log but don't fail
			}
			if len(progresses) > 0 {
				m.WriteProgressFile(progresses)
			}
		}
	}
}

// Cleanup removes the progress tracking table.
func (m *Monitor) Cleanup(ctx context.Context) error {
	query := fmt.Sprintf("DROP TABLE IF EXISTS %s", m.dbTable)
	m.connMu.Lock()
	_, err := m.conn.Exec(ctx, query)
	m.connMu.Unlock()
	return err
}

// truncateFilename truncates a filename to fit in maxLen characters.
func truncateFilename(filename string, maxLen int) string {
	if len(filename) <= maxLen {
		return filename
	}
	return "..." + filename[len(filename)-(maxLen-3):]
}

// FormatNumber formats a number with thousands separators.
func FormatNumber(n int64) string {
	p := message.NewPrinter(language.English)
	return p.Sprintf("%d", n)
}

// FormatRate formats a rate as "N/s" with thousands separators.
func FormatRate(rate int64) string {
	p := message.NewPrinter(language.English)
	return p.Sprintf("%d/s", rate)
}

// FormatPercentage formats a percentage with 2 decimal places.
func FormatPercentage(pct float64) string {
	return fmt.Sprintf("%.2f%%", pct)
}
