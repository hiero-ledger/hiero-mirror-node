// SPDX-License-Identifier: Apache-2.0

package cmd

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/spf13/cobra"

	"mirrornode-bootstrap/internal/database"
	"mirrornode-bootstrap/internal/importer"
	"mirrornode-bootstrap/internal/manifest"
	"mirrornode-bootstrap/internal/progress"
	"mirrornode-bootstrap/internal/tracking"
	"mirrornode-bootstrap/internal/worker"
)

func newImportCmd() *cobra.Command {
	var dataDir string
	var manifestFile string
	var maxJobs int

	cmd := &cobra.Command{
		Use:          "import",
		Short:        "Import data files into the database",
		Long:         "Imports gzipped CSV files from the data directory into PostgreSQL using parallel COPY.\nAutomatically resumes from previous state (skips already imported files).",
		SilenceUsage: true, // Don't print usage on errors (especially interrupts)
		RunE: func(cmd *cobra.Command, args []string) error {
			return runImport(cmd.Context(), dataDir, manifestFile, maxJobs)
		},
	}

	cmd.Flags().StringVarP(&dataDir, "data-dir", "d", "", "Directory containing data files (required)")
	cmd.Flags().StringVarP(&manifestFile, "manifest", "m", "", "Path to manifest.csv file (required)")
	cmd.Flags().IntVarP(&maxJobs, "jobs", "j", 0, "Number of parallel import jobs (default: 8)")

	cmd.MarkFlagRequired("data-dir")
	cmd.MarkFlagRequired("manifest")

	return cmd
}

func runImport(ctx context.Context, dataDir, manifestFile string, maxJobs int) error {
	startTime := time.Now()

	// Set defaults - use 8 as default since DB capacity may differ from local machine
	const defaultJobs = 8
	if maxJobs <= 0 {
		maxJobs = defaultJobs
	}

	// Override from config if set
	if cfg.MaxJobs > 0 && maxJobs == defaultJobs {
		maxJobs = cfg.MaxJobs
	}
	if cfg.DataDir != "" && dataDir == "" {
		dataDir = cfg.DataDir
	}
	if cfg.ManifestFile != "" && manifestFile == "" {
		manifestFile = cfg.ManifestFile
	}

	// Get executable directory and create bootstrap-logs/ subdirectory
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("failed to get executable path: %w", err)
	}
	logsDir := filepath.Join(filepath.Dir(exePath), "bootstrap-logs")
	if err := os.MkdirAll(logsDir, 0755); err != nil {
		return fmt.Errorf("failed to create logs directory: %w", err)
	}

	// PID file for single-instance enforcement
	pidFile := filepath.Join(logsDir, "bootstrap.pid")
	if pidData, err := os.ReadFile(pidFile); err == nil {
		var existingPID int
		if _, err := fmt.Sscanf(string(pidData), "%d", &existingPID); err == nil && existingPID > 0 {
			if process, err := os.FindProcess(existingPID); err == nil {
				// Signal 0 checks if process exists without affecting it
				if process.Signal(syscall.Signal(0)) == nil {
					return fmt.Errorf("another import process is already running (PID %d). If this is stale, remove %s", existingPID, pidFile)
				}
			}
		}
	}
	if err := os.WriteFile(pidFile, []byte(fmt.Sprintf("%d\n", os.Getpid())), 0644); err != nil {
		return fmt.Errorf("failed to write PID file: %w", err)
	}
	defer os.Remove(pidFile)

	// Setup file logging (bootstrap.log) in bootstrap-logs/ directory
	logFilePath := filepath.Join(logsDir, "bootstrap.log")
	logFile, err = os.OpenFile(logFilePath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("failed to open log file: %w", err)
	}
	defer logFile.Close()

	// Multi-writer: log to both stderr and file
	multiWriter := io.MultiWriter(os.Stderr, logFile)

	// Log level from DEBUG_MODE env var
	logLevel := slog.LevelInfo
	if os.Getenv("DEBUG_MODE") == "true" {
		logLevel = slog.LevelDebug
	}
	logger = slog.New(slog.NewTextHandler(multiWriter, &slog.HandlerOptions{Level: logLevel}))

	// Discrepancy file path - only created if there are mismatches
	discrepancyPath := filepath.Join(logsDir, "bootstrap_discrepancies.log")
	var discrepancyFile *os.File
	defer func() {
		if discrepancyFile != nil {
			discrepancyFile.Close()
		}
	}()

	logger.Info("Starting import",
		"data_dir", dataDir,
		"manifest", manifestFile,
		"jobs", maxJobs,
	)

	// Check if database has been initialized (SKIP_DB_INIT flag exists)
	// Bash behavior: init must run first, then all imports use mirror_node credentials
	skipDBInitFlag := filepath.Join(logsDir, database.SkipDBInitFlag)
	if _, err := os.Stat(skipDBInitFlag); err != nil {
		return fmt.Errorf("database not initialized. Run 'mirrornode-bootstrap init' first")
	}

	// Use mirror_node credentials (matches bash behavior after init)
	logger.Info("Database initialized, using mirror_node credentials")
	cfg.PGUser = "mirror_node"
	cfg.PGDatabase = "mirror_node"
	cfg.PGPassword = cfg.OwnerPassword

	// Load manifest
	mf, err := manifest.Load(manifestFile, dataDir)
	if err != nil {
		return fmt.Errorf("failed to load manifest: %w", err)
	}
	logger.Info("Manifest loaded",
		"files", numPrinter.Sprintf("%d", mf.Count()),
		"total_rows", numPrinter.Sprintf("%d", mf.TotalExpectedRows()),
	)

	// Setup tracking in bootstrap-logs/ directory
	trackingPath := filepath.Join(logsDir, cfg.TrackingFile)
	tracker := tracking.NewTracker(trackingPath)
	if err := tracker.Open(); err != nil {
		return fmt.Errorf("failed to open tracking file: %w", err)
	}
	defer tracker.Close()

	// Pre-populate tracking for crash recovery; skip special files
	allFiles := mf.AllFiles()
	for _, filename := range allFiles {
		if importer.IsSpecialFile(filename) {
			continue
		}
		basename := filepath.Base(filename)
		// Only add if not already tracked (for resume scenarios)
		status, _, _ := tracker.ReadStatus(basename)
		if status == "" || status == tracking.StatusNotStarted {
			tracker.WriteStatus(basename, tracking.StatusNotStarted, tracking.HashUnverified)
		}
	}
	logger.Info("Initialized tracking file", "path", trackingPath)

	// Create connection pool sized to worker count
	poolConfig, err := pgxpool.ParseConfig(cfg.PgxConnectionString())
	if err != nil {
		return fmt.Errorf("failed to parse connection string: %w", err)
	}
	poolConfig.MaxConns = int32(maxJobs + 2) // workers + monitor + buffer
	poolConfig.MinConns = 0                  // Don't pre-warm, let connections be created on demand

	// AfterRelease: Validate connection is usable before returning to pool
	poolConfig.AfterRelease = func(conn *pgx.Conn) bool {
		// Quick check if connection is still alive
		return conn.IsClosed() == false
	}

	pool, err := pgxpool.NewWithConfig(ctx, poolConfig)
	if err != nil {
		return fmt.Errorf("failed to create connection pool: %w", err)
	}
	defer pool.Close()

	// Get a connection for progress monitor
	conn, err := pool.Acquire(ctx)
	if err != nil {
		return fmt.Errorf("failed to acquire monitor connection: %w", err)
	}
	defer conn.Release()

	logger.Info("Connected to database",
		"host", cfg.PGHost,
		"database", cfg.PGDatabase,
		"pool_size", maxJobs+2,
	)

	// Setup progress monitor in bootstrap-logs/ directory
	progressPath := filepath.Join(logsDir, cfg.ProgressFile)
	monitor := progress.NewMonitor(conn.Conn(), 5*time.Second, progressPath)
	if err := monitor.CreateProgressTable(ctx); err != nil {
		logger.Warn("Failed to create progress table", "error", err)
	}

	// Resumption cleanup: reset files left in non-terminal states from a previous interrupted run
	// IN_PROGRESS = interrupted mid-import, FAILED_TO_IMPORT = connection/COPY error, FAILED_VALIDATION = hash/size mismatch
	var dirtyFiles []string
	for _, status := range []tracking.Status{tracking.StatusInProgress, tracking.StatusFailedToImport, tracking.StatusFailedValidation} {
		files, _ := tracker.GetFilesWithStatus(status)
		dirtyFiles = append(dirtyFiles, files...)
	}
	if len(dirtyFiles) > 0 {
		logger.Info("Resumption: resetting files from previous interrupted run", "count", len(dirtyFiles))
		for _, filename := range dirtyFiles {
			prevStatus, _, _ := tracker.ReadStatus(filename)
			logger.Info("Resetting file for re-import", "file", filename, "previous_status", prevStatus)
			tracker.WriteStatus(filename, tracking.StatusNotStarted, tracking.HashUnverified)
		}
		logger.Info("Resumption cleanup complete", "files_reset", len(dirtyFiles))
	}

	// Start progress monitor in background (writes to file)
	monitorCtx, cancelMonitor := context.WithCancel(ctx)
	go monitor.Run(monitorCtx)
	defer cancelMonitor()

	// Setup graceful shutdown
	ctx, cancel := signal.NotifyContext(ctx, syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	// Create worker pool
	workerPool := worker.NewPool(ctx, maxJobs)

	// Process function for each file
	processor := func(ctx context.Context, job worker.Job) worker.Result {
		logger.Debug("Worker received job", "file", job.Filename, "index", job.Index)
		result := worker.Result{Job: job}

		// Skip already imported files
		imported, err := tracker.IsImported(job.Filename)
		if err == nil && imported {
			logger.Debug("Skipping already imported file", "file", job.Filename)
			result.Success = true
			logger.Debug("Worker completed job (skipped)", "file", job.Filename)
			return result
		}

		// Get manifest entry
		entry, ok := mf.Get(job.Filename)
		if !ok {
			logger.Error("File not in manifest", "file", job.Filename)
			result.Error = fmt.Errorf("file not in manifest: %s", job.Filename)
			return result
		}

		// Acquire connection from pool first (don't block on other operations)
		acquireStart := time.Now()
		logger.Debug("Acquiring connection",
			"file", job.Filename,
			"pool_total", pool.Stat().TotalConns(),
			"pool_idle", pool.Stat().IdleConns(),
			"pool_acquired", pool.Stat().AcquiredConns(),
		)
		workerConn, err := pool.Acquire(ctx)
		acquireTime := time.Since(acquireStart)
		if acquireTime > 100*time.Millisecond {
			logger.Warn("Slow connection acquire",
				"file", job.Filename,
				"acquire_time_ms", acquireTime.Milliseconds(),
			)
		}
		if err != nil {
			logger.Error("Connection failed",
				"file", job.Filename,
				"error", err,
				"acquire_time_ms", acquireTime.Milliseconds(),
			)
			tracker.WriteStatus(job.Filename, tracking.StatusFailedToImport, tracking.HashUnverified)
			result.Error = fmt.Errorf("connection failed: %w", err)
			return result
		}
		defer workerConn.Release()

		// Register for progress tracking (fire-and-forget, non-critical)
		go monitor.RegisterFile(ctx, job.Filename, entry.RowCount)

		// Log start of processing
		logger.Info("Starting file import",
			"file", job.Filename,
			"expected_rows", numPrinter.Sprintf("%d", entry.RowCount),
		)

		// Update tracking to in-progress
		tracker.WriteStatus(job.Filename, tracking.StatusInProgress, tracking.HashUnverified)

		// Perform single-pass hash + import (no separate validation pass)
		importResult := importer.ImportWithValidation(ctx, workerConn.Conn(), job.FilePath, entry.Blake3Hash, entry.FileSize, cfg.DecompressorThreads)
		if importResult.Error != nil {
			// Check for context cancellation (user interrupted)
			if ctx.Err() != nil {
				logger.Info("Import interrupted",
					"file", job.Filename,
					"reason", ctx.Err().Error(),
				)
				tracker.WriteStatus(job.Filename, tracking.StatusInProgress, tracking.HashUnverified)
				result.Error = ctx.Err()
				return result
			}

			errStr := importResult.Error.Error()

			// Size mismatch (fast-fail before COPY)
			if strings.Contains(errStr, "size mismatch") {
				logger.Error("SIZE_MISMATCH",
					"file", job.Filename,
					"expected_bytes", entry.FileSize,
					"actual_bytes", importResult.ActualSize,
				)
				tracker.WriteStatus(job.Filename, tracking.StatusFailedValidation, tracking.HashUnverified)
				result.Error = importResult.Error
				return result
			}

			// Hash mismatch (after COPY, transaction rolled back)
			if strings.Contains(errStr, "hash mismatch") {
				logger.Error("HASH_MISMATCH",
					"file", job.Filename,
					"expected_hash", entry.Blake3Hash,
					"actual_hash", importResult.ActualHash,
				)
				tracker.WriteStatus(job.Filename, tracking.StatusFailedValidation, tracking.HashUnverified)
				result.Error = importResult.Error
				return result
			}

			// Database/COPY error
			logger.Error("Import failed",
				"file", job.Filename,
				"table", importResult.TableName,
				"error", importResult.Error,
			)
			tracker.WriteStatus(job.Filename, tracking.StatusFailedToImport, tracking.HashUnverified)
			result.Error = importResult.Error
			return result
		}

		// Track expected rows for discrepancy reporting
		result.ExpectedRows = entry.RowCount
		result.RowsImported = importResult.RowsImported

		// Verify row count if manifest has expected count
		if entry.RowCount > 0 && importResult.RowsImported != entry.RowCount {
			result.RowCountMismatch = true
			logger.Warn("ROW_COUNT_MISMATCH",
				"file", job.Filename,
				"expected_rows", numPrinter.Sprintf("%d", entry.RowCount),
				"actual_rows", numPrinter.Sprintf("%d", importResult.RowsImported),
			)
		}

		// Mark complete
		tracker.WriteStatus(job.Filename, tracking.StatusImported, tracking.HashVerified)
		monitor.MarkComplete(ctx, job.Filename)

		result.Success = true
		logger.Info("File imported",
			"file", job.Filename,
			"table", importResult.TableName,
			"rows", numPrinter.Sprintf("%d", importResult.RowsImported),
		)

		return result
	}

	// Start worker pool
	workerPool.Start(processor)

	// Collect jobs to submit (skip special files and already imported)
	type jobEntry struct {
		filename string
		filePath string
		fileSize int64
	}
	var jobsToSubmit []jobEntry
	var skippedCount int

	allFiles = mf.AllFiles()
	for _, filename := range allFiles {
		if importer.IsSpecialFile(filename) {
			continue
		}

		// Resume: skip if already imported
		basename := filepath.Base(filename)
		imported, _ := tracker.IsImported(basename)
		if imported {
			skippedCount++
			continue
		}

		// Log retries for previously failed files
		status, _, _ := tracker.ReadStatus(basename)
		if status == tracking.StatusFailedToImport || status == tracking.StatusFailedValidation {
			logger.Warn("Retrying previously failed file", "file", basename, "previous_status", status)
		}

		entry, _ := mf.Get(basename)
		jobsToSubmit = append(jobsToSubmit, jobEntry{
			filename: filepath.Base(filename),
			filePath: mf.FullPath(manifest.Entry{Filename: filename}),
			fileSize: entry.FileSize,
		})
	}

	// Submit jobs in a goroutine to avoid deadlock:
	// Submit blocks when jobs channel is full (20 slots)
	// Workers block when results channel is full (20 slots)
	// Results consumer won't start until Submit loop finishes - DEADLOCK!
	// Solution: Submit in goroutine, start consuming results immediately
	go func() {
		for i, j := range jobsToSubmit {
			workerPool.Submit(worker.Job{
				Filename: j.filename,
				FilePath: j.filePath,
				Index:    i,
			})
		}
		// Close after all jobs submitted
		workerPool.Close()
	}()

	// Count expected files (excluding special files and already imported)
	expectedCount := 0
	for _, filename := range allFiles {
		if importer.IsSpecialFile(filename) {
			continue
		}
		imported, _ := tracker.IsImported(filepath.Base(filename))
		if !imported {
			expectedCount++
		}
	}

	var totalRows int64
	var successCount, failCount, discrepancyCount int
	wasInterrupted := false
	for result := range workerPool.Results() {
		if result.Success {
			successCount++
			totalRows += result.RowsImported
		} else {
			failCount++
			logger.Error("Import failed",
				"file", result.Job.Filename,
				"error", result.Error,
			)
		}
		// Check for row count discrepancy - write immediately
		if result.RowCountMismatch {
			discrepancyCount++
			// Lazy file creation on first discrepancy, then append
			if discrepancyFile == nil {
				discrepancyFile, _ = os.OpenFile(discrepancyPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0644)
			}
			if discrepancyFile != nil {
				fmt.Fprintf(discrepancyFile, "%s: expected=%d, imported=%d\n",
					result.Job.Filename, result.ExpectedRows, result.RowsImported)
			}
		}
	}

	// Check if we were interrupted
	if ctx.Err() != nil {
		wasInterrupted = true
	}

	// Cleanup
	monitor.Cleanup(ctx)

	elapsed := time.Since(startTime)

	// Check for inconsistent files (stuck in IN_PROGRESS - crashed during import)
	inconsistentFiles, _ := tracker.GetFilesWithStatus(tracking.StatusInProgress)
	inconsistentCount := len(inconsistentFiles)

	// Print comprehensive statistics (matching bash print_final_statistics)
	logger.Info("====================================================")
	// Count special files (schema.sql.gz, MIRRORNODE_VERSION.gz) handled by init
	specialCount := 0
	for _, f := range mf.AllFiles() {
		if importer.IsSpecialFile(f) {
			specialCount++
		}
	}

	logger.Info("Import statistics:")
	logger.Info(numPrinter.Sprintf("  Total files in manifest: %d", mf.Count()))
	logger.Info(numPrinter.Sprintf("  Special files (handled by init): %d", specialCount))
	logger.Info(numPrinter.Sprintf("  Files skipped (already imported): %d", skippedCount))
	logger.Info(numPrinter.Sprintf("  Files attempted to import: %d", successCount+failCount))
	logger.Info(numPrinter.Sprintf("  Files completed: %d", successCount))
	logger.Info(numPrinter.Sprintf("  Files failed: %d", failCount))
	logger.Info(numPrinter.Sprintf("  Files with inconsistent status: %d", inconsistentCount))
	logger.Info(numPrinter.Sprintf("  Total files with issues: %d", failCount+inconsistentCount))
	logger.Info(numPrinter.Sprintf("  Total rows imported: %d", totalRows))
	logger.Info(fmt.Sprintf("  Duration: %s", elapsed.Round(time.Second)))
	logger.Info("====================================================")

	// Report discrepancies (row count mismatches)
	if discrepancyCount > 0 {
		logger.Warn("====================================================")
		logger.Warn(fmt.Sprintf("Discrepancies detected: %d files had row count mismatches", discrepancyCount))
		logger.Warn("See bootstrap_discrepancies.log for details")
		logger.Warn("====================================================")
	} else {
		logger.Info("No discrepancies detected during import.")
	}

	// Report inconsistent files
	if inconsistentCount > 0 {
		logger.Warn("====================================================")
		logger.Warn(fmt.Sprintf("Inconsistent status: %d files still marked IN_PROGRESS", inconsistentCount))
		for _, f := range inconsistentFiles {
			logger.Warn(fmt.Sprintf("  - %s", f))
		}
		logger.Warn("These files may need re-import")
		logger.Warn("====================================================")
	}

	// Final status - check for interruption, errors, or incomplete import
	processedCount := successCount + failCount
	pendingCount := expectedCount - processedCount

	if wasInterrupted {
		logger.Warn("====================================================")
		logger.Warn("Import was interrupted by signal.")
		logger.Warn(numPrinter.Sprintf("  Files completed before interrupt: %d", successCount))
		logger.Warn(numPrinter.Sprintf("  Files not started: %d", pendingCount))
		logger.Warn("Run the import command again to resume.")
		logger.Warn("====================================================")
		return fmt.Errorf("import interrupted")
	}

	if failCount > 0 || discrepancyCount > 0 || inconsistentCount > 0 {
		logger.Error("====================================================")
		logger.Error("The database import encountered errors.")
		logger.Error("Mirrornode requires a fully synchronized database.")
		logger.Error("Please review the errors and discrepancies above.")
		logger.Error("====================================================")
		return fmt.Errorf("%d files failed, %d discrepancies, %d inconsistent", failCount, discrepancyCount, inconsistentCount)
	}

	// Check if all files were processed
	if pendingCount > 0 {
		logger.Warn("====================================================")
		logger.Warn(numPrinter.Sprintf("Import incomplete: %d files not processed", pendingCount))
		logger.Warn("Run the import command again to continue.")
		logger.Warn("====================================================")
		return fmt.Errorf("%d files not processed", pendingCount)
	}

	logger.Info("====================================================")
	logger.Info("DB import completed successfully.")
	logger.Info("The database is fully identical to the data files.")
	logger.Info("====================================================")
	return nil
}
