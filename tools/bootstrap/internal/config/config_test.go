package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()

	if cfg.PGHost != "localhost" {
		t.Errorf("Expected PGHost=localhost, got %s", cfg.PGHost)
	}
	if cfg.PGPort != "5432" {
		t.Errorf("Expected PGPort=5432, got %s", cfg.PGPort)
	}
	if cfg.MaxJobs != 8 {
		t.Errorf("Expected MaxJobs=8, got %d", cfg.MaxJobs)
	}
	if cfg.PGUser != "postgres" {
		t.Errorf("Expected PGUser=postgres, got %s", cfg.PGUser)
	}
	if cfg.PGDatabase != "mirror_node" {
		t.Errorf("Expected PGDatabase=mirror_node, got %s", cfg.PGDatabase)
	}
	if cfg.IsGCPCloudSQL {
		t.Error("Expected IsGCPCloudSQL=false")
	}
	if !cfg.CreateMirrorAPIUser {
		t.Error("Expected CreateMirrorAPIUser=true by default")
	}
	if cfg.DecompressorThreads != 4 {
		t.Errorf("Expected DecompressorThreads=4, got %d", cfg.DecompressorThreads)
	}
	if cfg.MaxJobs != 8 {
		t.Errorf("Expected MaxJobs=8, got %d", cfg.MaxJobs)
	}
	if cfg.TrackingFile != "tracking.json" {
		t.Errorf("Expected TrackingFile=tracking.json, got %s", cfg.TrackingFile)
	}
	if cfg.ProgressFile != "progress.txt" {
		t.Errorf("Expected ProgressFile=progress.txt, got %s", cfg.ProgressFile)
	}
}

func TestLoadFromEnvFile(t *testing.T) {
	content := `# PostgreSQL environment variables
export PGUSER="testuser"
export PGPASSWORD="testpass"
export PGDATABASE="testdb"
export PGHOST="testhost"
export PGPORT="5433"

export IS_GCP_CLOUD_SQL="true"
export DECOMPRESSOR_THREADS=8
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	if cfg.PGUser != "testuser" {
		t.Errorf("Expected PGUser=testuser, got %s", cfg.PGUser)
	}
	if cfg.PGPassword != "testpass" {
		t.Errorf("Expected PGPassword=testpass, got %s", cfg.PGPassword)
	}
	if cfg.PGDatabase != "testdb" {
		t.Errorf("Expected PGDatabase=testdb, got %s", cfg.PGDatabase)
	}
	if cfg.PGHost != "testhost" {
		t.Errorf("Expected PGHost=testhost, got %s", cfg.PGHost)
	}
	if cfg.PGPort != "5433" {
		t.Errorf("Expected PGPort=5433, got %s", cfg.PGPort)
	}
	if !cfg.IsGCPCloudSQL {
		t.Error("Expected IsGCPCloudSQL=true")
	}
	if cfg.DecompressorThreads != 8 {
		t.Errorf("Expected DecompressorThreads=8, got %d", cfg.DecompressorThreads)
	}
}

func TestLoadFromEnvFile_AllPasswords(t *testing.T) {
	content := `export GRAPHQL_PASSWORD="graphql123"
export GRPC_PASSWORD="grpc123"
export IMPORTER_PASSWORD="importer123"
export OWNER_PASSWORD="owner123"
export REST_PASSWORD="rest123"
export REST_JAVA_PASSWORD="restjava123"
export ROSETTA_PASSWORD="rosetta123"
export WEB3_PASSWORD="web3123"
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	if cfg.GraphQLPassword != "graphql123" {
		t.Errorf("GraphQLPassword: expected graphql123, got %s", cfg.GraphQLPassword)
	}
	if cfg.GRPCPassword != "grpc123" {
		t.Errorf("GRPCPassword: expected grpc123, got %s", cfg.GRPCPassword)
	}
	if cfg.ImporterPassword != "importer123" {
		t.Errorf("ImporterPassword: expected importer123, got %s", cfg.ImporterPassword)
	}
	if cfg.OwnerPassword != "owner123" {
		t.Errorf("OwnerPassword: expected owner123, got %s", cfg.OwnerPassword)
	}
	if cfg.RESTPassword != "rest123" {
		t.Errorf("RESTPassword: expected rest123, got %s", cfg.RESTPassword)
	}
	if cfg.RESTJavaPassword != "restjava123" {
		t.Errorf("RESTJavaPassword: expected restjava123, got %s", cfg.RESTJavaPassword)
	}
	if cfg.RosettaPassword != "rosetta123" {
		t.Errorf("RosettaPassword: expected rosetta123, got %s", cfg.RosettaPassword)
	}
	if cfg.Web3Password != "web3123" {
		t.Errorf("Web3Password: expected web3123, got %s", cfg.Web3Password)
	}
}

func TestLoadFromEnvFile_Paths(t *testing.T) {
	content := `export DATA_DIR="/data/mirrornode"
export MANIFEST_FILE="/data/manifest.csv"
export TRACKING_FILE="custom_tracking.txt"
export PROGRESS_FILE="custom_progress.txt"
export MAX_JOBS=32
export CREATE_MIRROR_API_USER="false"
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	if cfg.DataDir != "/data/mirrornode" {
		t.Errorf("DataDir: expected /data/mirrornode, got %s", cfg.DataDir)
	}
	if cfg.ManifestFile != "/data/manifest.csv" {
		t.Errorf("ManifestFile: expected /data/manifest.csv, got %s", cfg.ManifestFile)
	}
	if cfg.TrackingFile != "custom_tracking.txt" {
		t.Errorf("TrackingFile: expected custom_tracking.txt, got %s", cfg.TrackingFile)
	}
	if cfg.ProgressFile != "custom_progress.txt" {
		t.Errorf("ProgressFile: expected custom_progress.txt, got %s", cfg.ProgressFile)
	}
	if cfg.MaxJobs != 32 {
		t.Errorf("MaxJobs: expected 32, got %d", cfg.MaxJobs)
	}
	if cfg.CreateMirrorAPIUser {
		t.Error("Expected CreateMirrorAPIUser=false")
	}
}

func TestLoadFromEnvFile_WithoutExport(t *testing.T) {
	content := `PGUSER=noexportuser
PGPASSWORD=noexportpass
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	if cfg.PGUser != "noexportuser" {
		t.Errorf("Expected PGUser=noexportuser, got %s", cfg.PGUser)
	}
}

func TestLoadFromEnv(t *testing.T) {
	cfg := DefaultConfig()

	// Set env vars
	os.Setenv("PGHOST", "envhost")
	os.Setenv("PGPORT", "5433")
	os.Setenv("PGUSER", "envuser")
	os.Setenv("PGPASSWORD", "envpass")
	os.Setenv("PGDATABASE", "envdb")
	os.Setenv("DATA_DIR", "/env/data")
	os.Setenv("MANIFEST_FILE", "/env/manifest.csv")
	os.Setenv("MAX_JOBS", "16")
	defer func() {
		os.Unsetenv("PGHOST")
		os.Unsetenv("PGPORT")
		os.Unsetenv("PGUSER")
		os.Unsetenv("PGPASSWORD")
		os.Unsetenv("PGDATABASE")
		os.Unsetenv("DATA_DIR")
		os.Unsetenv("MANIFEST_FILE")
		os.Unsetenv("MAX_JOBS")
	}()

	cfg.LoadFromEnv()

	if cfg.PGHost != "envhost" {
		t.Errorf("Expected PGHost=envhost, got %s", cfg.PGHost)
	}
	if cfg.PGPort != "5433" {
		t.Errorf("Expected PGPort=5433, got %s", cfg.PGPort)
	}
	if cfg.PGUser != "envuser" {
		t.Errorf("Expected PGUser=envuser, got %s", cfg.PGUser)
	}
	if cfg.PGPassword != "envpass" {
		t.Errorf("Expected PGPassword=envpass, got %s", cfg.PGPassword)
	}
	if cfg.PGDatabase != "envdb" {
		t.Errorf("Expected PGDatabase=envdb, got %s", cfg.PGDatabase)
	}
	if cfg.DataDir != "/env/data" {
		t.Errorf("Expected DataDir=/env/data, got %s", cfg.DataDir)
	}
	if cfg.ManifestFile != "/env/manifest.csv" {
		t.Errorf("Expected ManifestFile=/env/manifest.csv, got %s", cfg.ManifestFile)
	}
	if cfg.MaxJobs != 16 {
		t.Errorf("Expected MaxJobs=16, got %d", cfg.MaxJobs)
	}
}

func TestLoadFromEnv_InvalidMaxJobs(t *testing.T) {
	cfg := DefaultConfig()
	originalMaxJobs := cfg.MaxJobs

	os.Setenv("MAX_JOBS", "invalid")
	defer os.Unsetenv("MAX_JOBS")

	cfg.LoadFromEnv()

	// Should keep original value for invalid input
	if cfg.MaxJobs != originalMaxJobs {
		t.Errorf("MaxJobs should stay %d for invalid input, got %d", originalMaxJobs, cfg.MaxJobs)
	}
}

func TestLoadFromEnv_ZeroMaxJobs(t *testing.T) {
	cfg := DefaultConfig()
	originalMaxJobs := cfg.MaxJobs

	os.Setenv("MAX_JOBS", "0")
	defer os.Unsetenv("MAX_JOBS")

	cfg.LoadFromEnv()

	// Should keep original value for zero
	if cfg.MaxJobs != originalMaxJobs {
		t.Errorf("MaxJobs should stay %d for zero, got %d", originalMaxJobs, cfg.MaxJobs)
	}
}

func TestConnectionString(t *testing.T) {
	cfg := &Config{
		PGHost:     "localhost",
		PGPort:     "5432",
		PGUser:     "user",
		PGPassword: "pass",
		PGDatabase: "db",
	}

	conn := cfg.ConnectionString()
	expected := "host=localhost port=5432 user=user password=pass dbname=db sslmode=disable"
	if conn != expected {
		t.Errorf("Expected %q, got %q", expected, conn)
	}
}

func TestPgxConnectionString(t *testing.T) {
	cfg := &Config{
		PGHost:     "localhost",
		PGPort:     "5432",
		PGUser:     "user",
		PGPassword: "pass",
		PGDatabase: "db",
	}

	conn := cfg.PgxConnectionString()
	expected := "postgres://user:pass@localhost:5432/db?sslmode=disable"
	if conn != expected {
		t.Errorf("Expected %q, got %q", expected, conn)
	}
}

func TestLoadFromEnvFile_MissingFile(t *testing.T) {
	_, err := LoadFromEnvFile("/nonexistent/file")
	if err == nil {
		t.Error("Expected error for missing file")
	}
}

func TestLoadFromEnvFile_Comments(t *testing.T) {
	content := `# This is a comment
export PGUSER="user"
# Another comment
  # Indented comment
export PGPASSWORD="pass"
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	if cfg.PGUser != "user" {
		t.Errorf("Expected PGUser=user, got %s", cfg.PGUser)
	}
	if cfg.PGPassword != "pass" {
		t.Errorf("Expected PGPassword=pass, got %s", cfg.PGPassword)
	}
}

func TestLoadFromEnvFile_SingleQuotes(t *testing.T) {
	content := `export PGUSER='singlequoteuser'
export PGPASSWORD='pass with spaces'
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	if cfg.PGUser != "singlequoteuser" {
		t.Errorf("Expected PGUser=singlequoteuser, got %s", cfg.PGUser)
	}
	if cfg.PGPassword != "pass with spaces" {
		t.Errorf("Expected PGPassword='pass with spaces', got %s", cfg.PGPassword)
	}
}

func TestLoadFromEnvFile_InvalidIntegers(t *testing.T) {
	content := `export DECOMPRESSOR_THREADS=notanumber
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "bootstrap.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	// Should use defaults for invalid integers
	defaults := DefaultConfig()
	if cfg.DecompressorThreads != defaults.DecompressorThreads {
		t.Errorf("Expected default DecompressorThreads=%d, got %d", defaults.DecompressorThreads, cfg.DecompressorThreads)
	}
}

func TestLoadFromEnvFile_EmptyFile(t *testing.T) {
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "empty.env")
	os.WriteFile(envFile, []byte(""), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed for empty file: %v", err)
	}

	// Should return defaults
	if cfg.PGHost != "localhost" {
		t.Errorf("Expected default PGHost=localhost, got %s", cfg.PGHost)
	}
}

func TestLoadFromEnvFile_MalformedLines(t *testing.T) {
	content := `no_equals_sign
export PGUSER="valid"
just_key=
=no_key
export     =spacey
`
	tmpDir := t.TempDir()
	envFile := filepath.Join(tmpDir, "malformed.env")
	os.WriteFile(envFile, []byte(content), 0644)

	cfg, err := LoadFromEnvFile(envFile)
	if err != nil {
		t.Fatalf("LoadFromEnvFile failed: %v", err)
	}

	// Valid line should still be parsed
	if cfg.PGUser != "valid" {
		t.Errorf("Expected PGUser=valid, got %s", cfg.PGUser)
	}
}

