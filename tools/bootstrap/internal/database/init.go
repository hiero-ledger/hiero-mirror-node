// Package database handles database initialization including schema and roles.
package database

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/jackc/pgx/v5"
)

const (
	// InitScriptURL is the URL to download the init.sh script from
	InitScriptURL = "https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/refs/heads/main/importer/src/main/resources/db/scripts/init.sh"
)

// InitConfig holds configuration for database initialization.
type InitConfig struct {
	// Admin connection (usually postgres user)
	AdminHost     string
	AdminPort     string
	AdminUser     string
	AdminPassword string
	AdminDatabase string

	// User passwords to set
	OwnerPassword    string
	GraphQLPassword  string
	GRPCPassword     string
	ImporterPassword string
	RESTPassword     string
	RESTJavaPassword string
	RosettaPassword  string
	Web3Password     string

	// Paths
	SchemaFile    string // Path to schema.sql
	DataDir       string // Directory containing schema.sql if not specified
	LogsDir       string // Directory for log/flag files (next to binary)
	InitScriptURL string // URL to download init.sh (optional, uses default if empty)

	// Options
	IsGCPCloudSQL       bool
	CreateMirrorAPIUser bool
}

const (
	// SkipDBInitFlag is the flag file that indicates init has already completed
	SkipDBInitFlag = "SKIP_DB_INIT"
)

// Initialize performs full database initialization: downloads and runs init.sh to create
// database and roles, executes schema.sql to create tables, verifies setup, and creates
// success flag on completion. Skips if already initialized.
func Initialize(ctx context.Context, cfg InitConfig) error {
	// Check for skip flag in logs directory
	flagPath := filepath.Join(cfg.LogsDir, SkipDBInitFlag)
	if _, err := os.Stat(flagPath); err == nil {
		return nil
	}

	// Find schema.sql
	schemaPath := cfg.SchemaFile
	if schemaPath == "" {
		schemaPath = filepath.Join(cfg.DataDir, "schema.sql")
	}

	if _, err := os.Stat(schemaPath); os.IsNotExist(err) {
		return fmt.Errorf("schema.sql not found at %s", schemaPath)
	}

	// Download init.sh
	initURL := cfg.InitScriptURL
	if initURL == "" {
		initURL = InitScriptURL
	}

	initScript, err := downloadInitScript(initURL)
	if err != nil {
		return fmt.Errorf("failed to download init.sh: %w", err)
	}
	defer os.Remove(initScript)

	// Run init.sh with environment variables
	if err := runInitScript(ctx, initScript, cfg); err != nil {
		return fmt.Errorf("init.sh failed: %w", err)
	}

	// Execute schema.sql as mirror_node user
	if err := executeSchema(ctx, cfg, schemaPath); err != nil {
		return fmt.Errorf("schema.sql failed: %w", err)
	}

	// Verify tables exist
	if err := verifyTables(ctx, cfg); err != nil {
		return fmt.Errorf("table verification failed: %w", err)
	}

	// Create skip flag for future runs
	if err := os.WriteFile(flagPath, []byte("Database initialized successfully\n"), 0644); err != nil {
		fmt.Fprintf(os.Stderr, "Warning: could not create %s: %v\n", flagPath, err)
	}

	return nil
}

// downloadInitScript downloads init.sh to a temporary file.
func downloadInitScript(url string) (string, error) {
	resp, err := http.Get(url)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("HTTP %d: %s", resp.StatusCode, resp.Status)
	}

	tmpFile, err := os.CreateTemp("", "init-*.sh")
	if err != nil {
		return "", err
	}
	defer tmpFile.Close()

	if _, err := io.Copy(tmpFile, resp.Body); err != nil {
		os.Remove(tmpFile.Name())
		return "", err
	}

	// Make executable
	if err := os.Chmod(tmpFile.Name(), 0755); err != nil {
		os.Remove(tmpFile.Name())
		return "", err
	}

	return tmpFile.Name(), nil
}

// runInitScript executes init.sh with the required environment variables.
func runInitScript(ctx context.Context, scriptPath string, cfg InitConfig) error {
	cmd := exec.CommandContext(ctx, scriptPath)

	// Set environment variables as the bash script expects
	cmd.Env = append(os.Environ(),
		fmt.Sprintf("PGHOST=%s", cfg.AdminHost),
		fmt.Sprintf("PGPORT=%s", cfg.AdminPort),
		fmt.Sprintf("PGUSER=%s", cfg.AdminUser),
		fmt.Sprintf("PGPASSWORD=%s", cfg.AdminPassword),
		fmt.Sprintf("PGDATABASE=%s", cfg.AdminDatabase),
		fmt.Sprintf("OWNER_PASSWORD=%s", cfg.OwnerPassword),
		fmt.Sprintf("GRAPHQL_PASSWORD=%s", cfg.GraphQLPassword),
		fmt.Sprintf("GRPC_PASSWORD=%s", cfg.GRPCPassword),
		fmt.Sprintf("IMPORTER_PASSWORD=%s", cfg.ImporterPassword),
		fmt.Sprintf("REST_PASSWORD=%s", cfg.RESTPassword),
		fmt.Sprintf("REST_JAVA_PASSWORD=%s", cfg.RESTJavaPassword),
		fmt.Sprintf("ROSETTA_PASSWORD=%s", cfg.RosettaPassword),
		fmt.Sprintf("WEB3_PASSWORD=%s", cfg.Web3Password),
		fmt.Sprintf("IS_GCP_CLOUD_SQL=%t", cfg.IsGCPCloudSQL),
		fmt.Sprintf("CREATE_MIRROR_API_USER=%t", cfg.CreateMirrorAPIUser),
	)

	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%w: %s", err, string(output))
	}

	return nil
}

// executeSchema runs schema.sql via psql as the mirror_node user.
// Uses psql because schema.sql may contain psql-specific metacommands.
func executeSchema(ctx context.Context, cfg InitConfig, schemaPath string) error {
	cmd := exec.CommandContext(ctx, "psql",
		"-v", "ON_ERROR_STOP=1",
		"-q",
		"-f", schemaPath,
	)

	// Set environment for mirror_node user connection
	cmd.Env = append(os.Environ(),
		fmt.Sprintf("PGHOST=%s", cfg.AdminHost),
		fmt.Sprintf("PGPORT=%s", cfg.AdminPort),
		"PGUSER=mirror_node",
		fmt.Sprintf("PGPASSWORD=%s", cfg.OwnerPassword),
		"PGDATABASE=mirror_node",
	)

	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("psql failed: %w: %s", err, string(output))
	}

	return nil
}

// verifyTables checks that expected tables exist in the database.
func verifyTables(ctx context.Context, cfg InitConfig) error {
	connString := fmt.Sprintf("postgres://mirror_node:%s@%s:%s/mirror_node?sslmode=disable",
		cfg.OwnerPassword, cfg.AdminHost, cfg.AdminPort)

	conn, err := pgx.Connect(ctx, connString)
	if err != nil {
		return fmt.Errorf("connect failed: %w", err)
	}
	defer conn.Close(ctx)

	// Check that at least some core tables exist
	coreTables := []string{
		"entity",
		"transaction",
		"crypto_transfer",
		"account_balance",
		"flyway_schema_history",
	}

	var missing []string
	for _, table := range coreTables {
		var exists bool
		err := conn.QueryRow(ctx, 
			"SELECT EXISTS(SELECT 1 FROM pg_class WHERE relname = $1 AND relnamespace = 'public'::regnamespace)",
			table).Scan(&exists)
		if err != nil || !exists {
			missing = append(missing, table)
		}
	}

	if len(missing) > 0 {
		return fmt.Errorf("missing tables: %s", strings.Join(missing, ", "))
	}

	return nil
}

// TestConnection verifies connectivity to the database.
func TestConnection(ctx context.Context, connString string) error {
	conn, err := pgx.Connect(ctx, connString)
	if err != nil {
		return err
	}
	defer conn.Close(ctx)

	return conn.Ping(ctx)
}

// GetTableCount returns the number of tables in the public schema.
func GetTableCount(ctx context.Context, conn *pgx.Conn) (int, error) {
	var count int
	err := conn.QueryRow(ctx, 
		"SELECT COUNT(*) FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind = 'r'").Scan(&count)
	return count, err
}
