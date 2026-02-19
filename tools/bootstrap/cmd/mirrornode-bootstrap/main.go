// SPDX-License-Identifier: Apache-2.0

// Package main implements the mirrornode-bootstrap CLI for database bootstrapping.
package main

import (
	"compress/gzip"
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
	"github.com/zeebo/blake3"
	"golang.org/x/text/language"
	"golang.org/x/text/message"

	"mirrornode-bootstrap/internal/buffers"
	"mirrornode-bootstrap/internal/config"
	"mirrornode-bootstrap/internal/database"
	"mirrornode-bootstrap/internal/importer"
	"mirrornode-bootstrap/internal/manifest"
	"mirrornode-bootstrap/internal/progress"
	"mirrornode-bootstrap/internal/tracking"
	"mirrornode-bootstrap/internal/worker"
)

var (
	cfgFile         string
	cfg             *config.Config
	logger          *slog.Logger
	logFile         *os.File
	discrepancyFile *os.File
	numPrinter      = message.NewPrinter(language.English) // for comma-formatted numbers
)

func main() {
	// Initial logger to stderr (will be updated with file output after data dir is known)
	logger = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelInfo}))

	// Setup signal handling for graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		logger.Warn("Received signal, shutting down...", "signal", sig)
		cancel() // Cancel context - all workers will stop
		// Give workers a moment to clean up, then exit
		time.Sleep(2 * time.Second)
		logger.Error("Forced shutdown")
		os.Exit(1)
	}()

	rootCmd := &cobra.Command{
		Use:     "mirrornode-bootstrap",
		Short:   "Mirror Node Database Bootstrap Tool",
		Long:    "High-performance tool for bootstrapping Mirror Node databases with parallel imports.",
		CompletionOptions: cobra.CompletionOptions{
			DisableDefaultCmd: true,
		},
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			var err error
			if cfgFile != "" {
				cfg, err = config.LoadFromEnvFile(cfgFile)
				if err != nil {
					return fmt.Errorf("failed to load config: %w", err)
				}
			} else {
				cfg = config.DefaultConfig()
			}
			cfg.LoadFromEnv()
			return nil
		},
	}

	rootCmd.PersistentFlags().StringVarP(&cfgFile, "config", "c", "", "Path to bootstrap.env config file")

	// Add commands
	rootCmd.AddCommand(newInitCmd())
	rootCmd.AddCommand(newImportCmd())
	rootCmd.AddCommand(newStatusCmd())
	rootCmd.AddCommand(newWatchCmd())

	if err := rootCmd.ExecuteContext(ctx); err != nil {
		os.Exit(1)
	}
}

func newInitCmd() *cobra.Command {
	var dataDir string
	var schemaFile string

	cmd := &cobra.Command{
		Use:   "init",
		Short: "Initialize database with schema and roles",
		Long:  "Downloads init.sh from GitHub, creates the database, roles, permissions, then executes schema.sql.",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runInit(cmd.Context(), dataDir, schemaFile)
		},
	}

	cmd.Flags().StringVarP(&dataDir, "data-dir", "d", "", "Directory containing schema.sql")
	cmd.Flags().StringVarP(&schemaFile, "schema", "s", "", "Path to schema.sql file (overrides data-dir)")

	return cmd
}

func runInit(ctx context.Context, dataDir, schemaFile string) error {
	// Apply config defaults
	if cfg.DataDir != "" && dataDir == "" {
		dataDir = cfg.DataDir
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

	logger.Info("Starting database initialization",
		"host", cfg.PGHost,
		"port", cfg.PGPort,
		"admin_user", cfg.PGUser,
	)

	// Validate and decompress schema.sql.gz from manifest
	if dataDir != "" && schemaFile == "" {
		manifestPath := filepath.Join(dataDir, "manifest.csv")
		if _, err := os.Stat(manifestPath); err == nil {
			// Load manifest
			mf, err := manifest.Load(manifestPath, dataDir)
			if err != nil {
				return fmt.Errorf("failed to load manifest: %w", err)
			}

			// Validate schema.sql.gz if present in manifest
			if entry, ok := mf.Get("schema.sql.gz"); ok {
				schemaGzPath := filepath.Join(dataDir, "schema.sql.gz")

				// Validate file size
				info, err := os.Stat(schemaGzPath)
				if err != nil {
					return fmt.Errorf("schema.sql.gz not found: %w", err)
				}
				if info.Size() != entry.FileSize {
					return fmt.Errorf("schema.sql.gz size mismatch: expected %d, got %d", entry.FileSize, info.Size())
				}

				// Validate BLAKE3 hash
				file, err := os.Open(schemaGzPath)
				if err != nil {
					return fmt.Errorf("failed to open schema.sql.gz: %w", err)
				}
				hasher := blake3.New()
				buf := buffers.GetDecompressBuffer()
				if _, err := io.CopyBuffer(hasher, file, buf); err != nil {
					file.Close()
					buffers.ReturnDecompressBuffer(buf)
					return fmt.Errorf("failed to hash schema.sql.gz: %w", err)
				}
				file.Close()
				buffers.ReturnDecompressBuffer(buf)

				actualHash := fmt.Sprintf("%x", hasher.Sum(nil))
				if actualHash != entry.Blake3Hash {
					return fmt.Errorf("schema.sql.gz hash mismatch: expected %s, got %s", entry.Blake3Hash, actualHash)
				}

				logger.Info("schema.sql.gz validated successfully", "size", entry.FileSize, "hash", actualHash[:16]+"...")

				// Decompress to schema.sql
				schemaPath := filepath.Join(dataDir, "schema.sql")
				gzFile, err := os.Open(schemaGzPath)
				if err != nil {
					return fmt.Errorf("failed to open schema.sql.gz for decompression: %w", err)
				}
				defer gzFile.Close()

				gzReader, err := gzip.NewReader(gzFile)
				if err != nil {
					return fmt.Errorf("failed to create gzip reader: %w", err)
				}
				defer gzReader.Close()

				outFile, err := os.Create(schemaPath)
				if err != nil {
					return fmt.Errorf("failed to create schema.sql: %w", err)
				}
				defer outFile.Close()

				if _, err := io.Copy(outFile, gzReader); err != nil {
					return fmt.Errorf("failed to decompress schema.sql.gz: %w", err)
				}

				logger.Info("schema.sql decompressed successfully", "path", schemaPath)
				schemaFile = schemaPath
			}
		}
	}

	initCfg := database.InitConfig{
		AdminHost:           cfg.PGHost,
		AdminPort:           cfg.PGPort,
		AdminUser:           cfg.PGUser,
		AdminPassword:       cfg.PGPassword,
		AdminDatabase:       cfg.PGDatabase,
		OwnerPassword:       cfg.OwnerPassword,
		GraphQLPassword:     cfg.GraphQLPassword,
		GRPCPassword:        cfg.GRPCPassword,
		ImporterPassword:    cfg.ImporterPassword,
		RESTPassword:        cfg.RESTPassword,
		RESTJavaPassword:    cfg.RESTJavaPassword,
		RosettaPassword:     cfg.RosettaPassword,
		Web3Password:        cfg.Web3Password,
		SchemaFile:          schemaFile,
		DataDir:             dataDir,
		LogsDir:             logsDir,
		IsGCPCloudSQL:       cfg.IsGCPCloudSQL,
		CreateMirrorAPIUser: cfg.CreateMirrorAPIUser,
	}

	if err := database.Initialize(ctx, initCfg); err != nil {
		logger.Error("Database initialization failed", "error", err)
		return err
	}

	logger.Info("Database initialization complete")
	return nil
}

func newImportCmd() *cobra.Command {
	var dataDir string
	var manifestFile string
	var maxJobs int

	cmd := &cobra.Command{
		Use:   "import",
		Short: "Import data files into the database",
		Long:  "Imports gzipped CSV files from the data directory into PostgreSQL using parallel COPY.\nAutomatically resumes from previous state (skips already imported files).",
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
	poolConfig.MinConns = 0                   // Don't pre-warm, let connections be created on demand

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

	// Close discrepancy file if it was opened
	if discrepancyFile != nil {
		discrepancyFile.Close()
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

func newStatusCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "status",
		Short: "Show import status",
		RunE: func(cmd *cobra.Command, args []string) error {
			// Get logs directory next to binary
			exePath, err := os.Executable()
			if err != nil {
				return fmt.Errorf("failed to get executable path: %w", err)
			}
			logsDir := filepath.Join(filepath.Dir(exePath), "bootstrap-logs")

			trackingPath := filepath.Join(logsDir, cfg.TrackingFile)
			tracker := tracking.NewTracker(trackingPath)
			if err := tracker.Open(); err != nil {
				return fmt.Errorf("failed to load tracking data: %w", err)
			}

			counts, err := tracker.CountByStatus()
			if err != nil {
				return err
			}

			fmt.Printf("Import Status (from %s):\n", trackingPath)
			fmt.Printf("  Imported:    %d\n", counts[tracking.StatusImported])
			fmt.Printf("  In Progress: %d\n", counts[tracking.StatusInProgress])
			fmt.Printf("  Failed:      %d\n", counts[tracking.StatusFailedToImport]+counts[tracking.StatusFailedValidation])
			fmt.Printf("  Not Started: %d\n", counts[tracking.StatusNotStarted])

			return nil
		},
	}

	return cmd
}

func newWatchCmd() *cobra.Command {
	var interval int
	var dataDir string
	var manifestFile string

	cmd := &cobra.Command{
		Use:   "watch",
		Short: "Watch live import progress",
		Long:  "Connects to the database and displays live COPY progress.\nRun this in a separate terminal while import is running.",
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := cmd.Context()

			// Use mirror_node credentials (same as import)
			cfg.PGUser = "mirror_node"
			cfg.PGDatabase = "mirror_node"
			cfg.PGPassword = cfg.OwnerPassword

			// Load manifest for expected row counts
			var mf *manifest.Manifest
			if manifestFile != "" {
				var err error
				mf, err = manifest.Load(manifestFile, dataDir)
				if err != nil {
					return fmt.Errorf("failed to load manifest: %w", err)
				}
				fmt.Printf("Loaded manifest with %d files\n", mf.Count())
			}

			// Connect to database
			conn, err := pgx.Connect(ctx, cfg.PgxConnectionString())
			if err != nil {
				return fmt.Errorf("failed to connect: %w", err)
			}
			defer conn.Close(ctx)

			// Create monitor
			monitor := progress.NewMonitor(conn, time.Duration(interval)*time.Second, "")
			startTime := time.Now()

			// Setup signal handling
			ctx, cancel := signal.NotifyContext(ctx, syscall.SIGINT, syscall.SIGTERM)
			defer cancel()

			fmt.Println("Watching import progress (Ctrl+C to stop)...")

			ticker := time.NewTicker(time.Duration(interval) * time.Second)
			defer ticker.Stop()

			for {
				select {
				case <-ctx.Done():
					fmt.Println("\nStopped.")
					return nil
				case <-ticker.C:
					progresses, err := monitor.FetchProgress(ctx)
					if err != nil {
						// Connection lost, try to display what we know
						fmt.Print("\033[2J\033[H")
						fmt.Println("Error fetching progress:", err)
						continue
					}
					// Enrich with manifest data if available
					if mf != nil {
						for i := range progresses {
							if entry, ok := mf.Get(progresses[i].Filename); ok {
								progresses[i].TotalRows = entry.RowCount
								if entry.RowCount > 0 {
									progresses[i].Percentage = float64(progresses[i].RowsProcessed) / float64(entry.RowCount) * 100
								}
							}
						}
					}
					printTerminalProgress(progresses, startTime)
				}
			}
		},
	}

	cmd.Flags().IntVarP(&interval, "interval", "i", 1, "Refresh interval in seconds")
	cmd.Flags().StringVarP(&dataDir, "data-dir", "d", "", "Directory containing data files")
	cmd.Flags().StringVarP(&manifestFile, "manifest", "m", "", "Path to manifest.csv file")

	return cmd
}

// printTerminalProgress outputs live progress to terminal
func printTerminalProgress(progresses []progress.FileProgress, startTime time.Time) {
	// Clear screen and move cursor to top (ANSI escape codes)
	fmt.Print("\033[2J\033[H")
	
	elapsed := time.Since(startTime).Round(time.Second)
	fmt.Printf("mirrornode-bootstrap - Import Progress (elapsed: %s)\n", elapsed)
	
	// Table: 45 filename + 30 rows/total + 8 pct + 12 rate + 5 spaces = 100 chars
	border := "════════════════════════════════════════════════════════════════════════════════════════════════════"
	divider := "────────────────────────────────────────────────────────────────────────────────────────────────────"
	
	fmt.Println(border)
	
	if len(progresses) == 0 {
		fmt.Println("No active imports...")
		fmt.Println(border)
		return
	}
	
	// Header
	fmt.Printf("%-45s %30s %8s %12s\n", "Filename", "Rows/Total", "%", "Rate")
	fmt.Println(divider)
	
	for _, p := range progresses {
		filename := p.Filename
		if len(filename) > 45 {
			filename = "..." + filename[len(filename)-42:]
		}
		
		pct := fmt.Sprintf("%.1f%%", p.Percentage)
		rate := numPrinter.Sprintf("%d/s", p.Rate)
		rowsTotal := numPrinter.Sprintf("%d / %d", p.RowsProcessed, p.TotalRows)
		
		fmt.Printf("%-45s %30s %8s %12s\n", filename, rowsTotal, pct, rate)
	}
	
	fmt.Println(border)
}
