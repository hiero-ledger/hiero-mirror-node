package database

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestInitConfig_Defaults(t *testing.T) {
	cfg := InitConfig{}

	if cfg.InitScriptURL != "" {
		t.Error("InitScriptURL should be empty by default")
	}
	if cfg.IsGCPCloudSQL {
		t.Error("IsGCPCloudSQL should be false by default")
	}
	if cfg.CreateMirrorAPIUser {
		t.Error("CreateMirrorAPIUser should be false by default")
	}
	if cfg.LogsDir != "" {
		t.Error("LogsDir should be empty by default")
	}
}

func TestInitScriptURL(t *testing.T) {
	// Verify the URL constant is valid
	if InitScriptURL == "" {
		t.Error("InitScriptURL constant should not be empty")
	}

	expected := "https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/refs/heads/main/importer/src/main/resources/db/scripts/init.sh"
	if InitScriptURL != expected {
		t.Errorf("InitScriptURL mismatch:\nexpected: %s\ngot: %s", expected, InitScriptURL)
	}
}

func TestSkipDBInitFlag(t *testing.T) {
	if SkipDBInitFlag != "SKIP_DB_INIT" {
		t.Errorf("SkipDBInitFlag should be 'SKIP_DB_INIT', got: %s", SkipDBInitFlag)
	}
}

func TestDownloadInitScript_Success(t *testing.T) {
	// Create a test server that returns a mock script
	scriptContent := "#!/bin/bash\necho 'test script'\n"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(scriptContent))
	}))
	defer server.Close()

	// Download the script
	path, err := downloadInitScript(server.URL)
	if err != nil {
		t.Fatalf("downloadInitScript failed: %v", err)
	}
	defer os.Remove(path)

	// Verify file exists
	if _, err := os.Stat(path); os.IsNotExist(err) {
		t.Error("Downloaded script file does not exist")
	}

	// Verify content
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read downloaded script: %v", err)
	}
	if string(content) != scriptContent {
		t.Errorf("Script content mismatch: expected %q, got %q", scriptContent, string(content))
	}

	// Verify executable permissions
	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("Failed to stat downloaded script: %v", err)
	}
	if info.Mode()&0111 == 0 {
		t.Error("Downloaded script is not executable")
	}
}

func TestDownloadInitScript_HTTPError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	_, err := downloadInitScript(server.URL)
	if err == nil {
		t.Error("Expected error for HTTP 404, got nil")
	}
}

func TestDownloadInitScript_InvalidURL(t *testing.T) {
	_, err := downloadInitScript("http://invalid.invalid.invalid/notfound")
	if err == nil {
		t.Error("Expected error for invalid URL, got nil")
	}
}

func TestInitialize_SkipsWhenFlagExists(t *testing.T) {
	// Create temp directory for logs
	logsDir := t.TempDir()
	
	// Create the skip flag
	flagPath := filepath.Join(logsDir, SkipDBInitFlag)
	if err := os.WriteFile(flagPath, []byte("initialized"), 0644); err != nil {
		t.Fatalf("Failed to create skip flag: %v", err)
	}

	cfg := InitConfig{
		LogsDir: logsDir,
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Should return nil immediately (skip initialization)
	err := Initialize(ctx, cfg)
	if err != nil {
		t.Errorf("Initialize should skip when flag exists, got error: %v", err)
	}
}

func TestInitialize_MissingSchemaFile(t *testing.T) {
	logsDir := t.TempDir()
	dataDir := t.TempDir()

	cfg := InitConfig{
		LogsDir:   logsDir,
		DataDir:   dataDir,
		SchemaFile: "", // Will look for schema.sql in DataDir
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	err := Initialize(ctx, cfg)
	if err == nil {
		t.Error("Expected error for missing schema.sql")
	}
	if err != nil && !contains(err.Error(), "schema.sql not found") {
		t.Errorf("Expected 'schema.sql not found' error, got: %v", err)
	}
}

func TestInitialize_SchemaFileFound(t *testing.T) {
	logsDir := t.TempDir()
	dataDir := t.TempDir()

	// Create a dummy schema.sql
	schemaPath := filepath.Join(dataDir, "schema.sql")
	if err := os.WriteFile(schemaPath, []byte("-- test schema"), 0644); err != nil {
		t.Fatalf("Failed to create schema.sql: %v", err)
	}

	// Create a mock server for init.sh that returns an error script
	// This tests that we properly reach the download phase
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		// Return a script that exits with error (to prevent full execution)
		w.Write([]byte("#!/bin/bash\nexit 1\n"))
	}))
	defer server.Close()

	cfg := InitConfig{
		LogsDir:       logsDir,
		DataDir:       dataDir,
		InitScriptURL: server.URL,
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	err := Initialize(ctx, cfg)
	// We expect an error from init.sh failing
	if err == nil {
		t.Error("Expected init.sh to fail")
	}
	if err != nil && !contains(err.Error(), "init.sh failed") {
		t.Errorf("Expected 'init.sh failed' error, got: %v", err)
	}
}

func TestInitialize_CustomSchemaPath(t *testing.T) {
	logsDir := t.TempDir()
	customDir := t.TempDir()

	// Create schema.sql in custom location
	schemaPath := filepath.Join(customDir, "custom_schema.sql")
	if err := os.WriteFile(schemaPath, []byte("-- custom schema"), 0644); err != nil {
		t.Fatalf("Failed to create schema.sql: %v", err)
	}

	// Create mock server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("#!/bin/bash\nexit 1\n"))
	}))
	defer server.Close()

	cfg := InitConfig{
		LogsDir:       logsDir,
		SchemaFile:    schemaPath, // Custom path
		InitScriptURL: server.URL,
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	err := Initialize(ctx, cfg)
	// init.sh fails, but we verify schema was found
	if err != nil && contains(err.Error(), "schema.sql not found") {
		t.Error("Should have found custom schema file")
	}
}

func TestRunInitScript_EnvVars(t *testing.T) {
	// Create a script that echoes env vars
	tmpDir := t.TempDir()
	scriptPath := filepath.Join(tmpDir, "test_env.sh")
	
	// Script that checks for required env vars
	script := `#!/bin/bash
if [ -z "$PGHOST" ]; then exit 1; fi
if [ -z "$PGPORT" ]; then exit 2; fi
if [ -z "$PGUSER" ]; then exit 3; fi
if [ -z "$OWNER_PASSWORD" ]; then exit 4; fi
exit 0
`
	if err := os.WriteFile(scriptPath, []byte(script), 0755); err != nil {
		t.Fatalf("Failed to create test script: %v", err)
	}

	cfg := InitConfig{
		AdminHost:     "localhost",
		AdminPort:     "5432",
		AdminUser:     "postgres",
		AdminPassword: "test",
		OwnerPassword: "owner123",
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	err := runInitScript(ctx, scriptPath, cfg)
	if err != nil {
		t.Errorf("runInitScript failed: %v", err)
	}
}

func TestRunInitScript_Timeout(t *testing.T) {
	tmpDir := t.TempDir()
	scriptPath := filepath.Join(tmpDir, "slow_script.sh")
	
	// Script that sleeps for 5 seconds
	script := "#!/bin/bash\nsleep 5\n"
	if err := os.WriteFile(scriptPath, []byte(script), 0755); err != nil {
		t.Fatalf("Failed to create test script: %v", err)
	}

	cfg := InitConfig{}

	// Short timeout - 200ms
	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()

	err := runInitScript(ctx, scriptPath, cfg)
	if err == nil {
		t.Error("Expected timeout error")
	}
}

func TestRunInitScript_ScriptFails(t *testing.T) {
	tmpDir := t.TempDir()
	scriptPath := filepath.Join(tmpDir, "failing_script.sh")
	
	script := "#!/bin/bash\necho 'error message' >&2\nexit 1\n"
	if err := os.WriteFile(scriptPath, []byte(script), 0755); err != nil {
		t.Fatalf("Failed to create test script: %v", err)
	}

	cfg := InitConfig{}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	err := runInitScript(ctx, scriptPath, cfg)
	if err == nil {
		t.Error("Expected error from failing script")
	}
}

func TestInitConfigFields(t *testing.T) {
	cfg := InitConfig{
		AdminHost:           "db.example.com",
		AdminPort:           "5433",
		AdminUser:           "admin",
		AdminPassword:       "adminpass",
		AdminDatabase:       "postgres",
		OwnerPassword:       "owner123",
		GraphQLPassword:     "graphql123",
		GRPCPassword:        "grpc123",
		ImporterPassword:    "importer123",
		RESTPassword:        "rest123",
		RESTJavaPassword:    "restjava123",
		RosettaPassword:     "rosetta123",
		Web3Password:        "web3123",
		SchemaFile:          "/path/to/schema.sql",
		DataDir:             "/data",
		LogsDir:             "/logs",
		InitScriptURL:       "http://example.com/init.sh",
		IsGCPCloudSQL:       true,
		CreateMirrorAPIUser: true,
	}

	if cfg.AdminHost != "db.example.com" {
		t.Error("AdminHost not set correctly")
	}
	if cfg.AdminPort != "5433" {
		t.Error("AdminPort not set correctly")
	}
	if !cfg.IsGCPCloudSQL {
		t.Error("IsGCPCloudSQL not set correctly")
	}
	if !cfg.CreateMirrorAPIUser {
		t.Error("CreateMirrorAPIUser not set correctly")
	}
}

// Helper function
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(s) > 0 && containsImpl(s, substr))
}

func containsImpl(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

// Note: Full integration tests require a database and are skipped in unit tests.
// They will be run separately during integration testing.
