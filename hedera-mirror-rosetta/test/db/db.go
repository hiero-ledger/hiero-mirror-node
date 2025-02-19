/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package db

import (
	"database/sql"
	"fmt"
	"github.com/ory/dockertest/v3"
	"os"
	"path"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	_ "github.com/lib/pq"
	"github.com/ory/dockertest/v3/docker"
	log "github.com/sirupsen/logrus"
	"github.com/thanhpk/randstr"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

const (
	dbCleanupScript = "../hedera-mirror-common/src/test/resources/cleanup.sql"
	dbMigrationPath = "../hedera-mirror-importer/src/main/resources/db/migration/v1"
	dbName          = "mirror_node"
	initScript      = "../hedera-mirror-importer/src/main/resources/db/scripts/init.sh"
	ownerUsername   = "mirror_node"
	poolMaxWait     = 5 * time.Minute
)

// moduleRoot is the absolute path to hedera-mirror-rosetta
var moduleRoot string

type DbResource struct {
	db       *sql.DB
	params   dbParams
	pool     *dockertest.Pool
	resource *dockertest.Resource
	network  *dockertest.Network
}

func CreateDbRecords(dbClient interfaces.DbClient, records ...interface{}) {
	for _, record := range records {
		dbClient.GetDb().Create(record)
	}
}

func ExecSql(dbClient interfaces.DbClient, sqls ...string) {
	for _, s := range sqls {
		dbClient.GetDb().Exec(s)
	}
}

// GetDbConfig returns the db config of the session
func (d DbResource) GetDbConfig() config.Db {
	return d.params.toConfig()
}

// GetDb returns the sql db pool
func (d DbResource) GetDb() *sql.DB {
	return d.db
}

// GetGormDb creates a gorm db session
func (d DbResource) GetGormDb() *gorm.DB {
	gdb, err := gorm.Open(postgres.New(postgres.Config{Conn: d.db}), &gorm.Config{})
	if err != nil {
		log.Fatalf("Failed to create gorm db session: %s", err)
	}

	return gdb
}

type dbParams struct {
	endpoint      string
	ownerPassword string
}

func (d dbParams) toDsn() string {
	return fmt.Sprintf("postgres://%s:%s@%s/%s?sslmode=disable", ownerUsername, d.ownerPassword, d.endpoint, dbName)
}

func (d dbParams) toJdbcUrl(endpoint string) string {
	return fmt.Sprintf("jdbc:postgresql://%s/%s", endpoint, dbName)
}

func (d dbParams) toConfig() config.Db {
	hostPort := strings.Split(d.endpoint, ":")
	port, _ := strconv.ParseUint(hostPort[1], 10, 16)
	return config.Db{
		Host:     hostPort[0],
		Name:     dbName,
		Password: d.ownerPassword,
		Pool: config.Pool{
			MaxIdleConnections: 20,
			MaxLifetime:        30,
			MaxOpenConnections: 100,
		},
		Port:     uint16(port),
		Username: ownerUsername,
	}
}

// CleanupDb cleans the data written to the db during tests
func CleanupDb(db *sql.DB) {
	filename := filepath.Clean(path.Join(moduleRoot, dbCleanupScript))
	script, err := os.ReadFile(filename)
	if err != nil {
		log.Fatalf("Failed to read cleanup.sql: %s", err)
	}

	_, err = db.Exec(string(script))
	if err != nil {
		log.Fatalf("Failed to run cleanup.sql: %s", err)
	}
}

func SetupDb(migrate bool) DbResource {
	var db *sql.DB

	pool, err := dockertest.NewPool("")
	if err != nil {
		log.Fatalf("Could not connect to docker: %s", err)
	}

	// set max wait, used in pool.Retry to timeout
	pool.MaxWait = poolMaxWait

	// create a dedicated network for the containers, so flyway can connect to db using hostname
	log.Info("Create network for docker containers")
	network, err := pool.CreateNetwork(randstr.Hex(8))
	if err != nil {
		log.Fatalf("Could not create docker network: %s", err)
	}

	log.Info("Create postgres container")
	resource, dbParams := createPostgresDb(pool, network)

	if err = pool.Retry(func() error {
		var err error
		db, err = sql.Open("postgres", dbParams.toDsn())
		if err != nil {
			log.Errorf("%s", err)
			return err
		}

		return db.Ping()
	}); err != nil {
		log.Fatalf("Could not connect to docker: %s", err)
	}

	if migrate {
		log.Info("Run flyway migration")
		runFlywayMigration(pool, network, dbParams)
	}

	return DbResource{
		db:       db,
		params:   dbParams,
		pool:     pool,
		resource: resource,
		network:  network,
	}
}

func TearDownDb(dbResource DbResource) {
	log.Info("Remove postgres container")
	if err := dbResource.pool.Purge(dbResource.resource); err != nil {
		log.Errorf("Failed to purge postgresql resource: %s", err)
	}

	log.Info("Remove container network")
	if err := dbResource.pool.RemoveNetwork(dbResource.network); err != nil {
		log.Errorf("Failed to remove network: %s", err)
	}
}

func createPostgresDb(pool *dockertest.Pool, network *dockertest.Network) (*dockertest.Resource, dbParams) {
	ownerPassword := randstr.Hex(12)
	env := []string{
		"OWNER_PASSWORD=" + ownerPassword,
		"POSTGRES_PASSWORD=postgres_password",
	}

	options := &dockertest.RunOptions{
		Name:       getDbHostname(network.Network),
		Repository: "postgres",
		Tag:        "16-alpine",
		Env:        env,
		Mounts:     []string{filepath.Clean(path.Join(moduleRoot, initScript)) + ":/docker-entrypoint-initdb.d/init.sh"},
		Networks:   []*dockertest.Network{network},
	}
	resource, err := pool.RunWithOptions(options)
	if err != nil {
		log.Fatalf("Could not start resource: %s", err)
	}

	return resource, dbParams{
		// use IPv4 local address, 'localhost' may resolve to IPv6 local address in github CI
		endpoint:      "127.0.0.1:" + resource.GetPort("5432/tcp"),
		ownerPassword: ownerPassword,
	}
}

func runFlywayMigration(pool *dockertest.Pool, network *dockertest.Network, params dbParams) {
	migrationPath := filepath.Clean(path.Join(moduleRoot, dbMigrationPath))
	// run the container with tty and entrypoint "bin/sh" so it will stay alive in background
	options := &dockertest.RunOptions{
		Repository: "flyway/flyway",
		Tag:        "9",
		Entrypoint: []string{"/bin/sh"},
		Networks:   []*dockertest.Network{network},
		Mounts:     []string{migrationPath + ":/flyway/sql"},
		Tty:        true,
	}

	resource, err := pool.RunWithOptions(options)
	if err != nil {
		log.Fatalf("Failed to run flyway: %s", err)
	}

	args := map[string]string{
		"password":                                      params.ownerPassword,
		"placeholders.api-password":                     "mirror_api",
		"placeholders.api-user":                         "mirror_api_password",
		"placeholders.partitionStartDate":               "'1970-01-01'",
		"placeholders.partitionTimeInterval":            "'10 years'",
		"placeholders.db-name":                          dbName,
		"placeholders.db-user":                          ownerUsername,
		"placeholders.topicRunningHashV2AddedTimestamp": "0",
		"target": "latest",
		"url":    params.toJdbcUrl(getDbHostname(network.Network) + ":5432"),
		"user":   ownerUsername,
	}

	cmd := []string{"flyway"}
	for name, value := range args {
		cmd = append(cmd, fmt.Sprintf("-%s=%s", name, value))
	}
	cmd = append(cmd, "migrate")

	// run the flyway migrate command and show its output
	code, execErr := resource.Exec(cmd, dockertest.ExecOptions{
		StdOut: os.Stdout,
		StdErr: os.Stderr,
	})

	if err := pool.Purge(resource); err != nil {
		log.Fatalf("Could not purge resource: %s", err)
	}

	if execErr != nil || code != 0 {
		log.Fatalf("Failed to run flyway: code - %d, err - %s", code, execErr)
	}
}

func getDbHostname(network *docker.Network) string {
	return network.Name + "_db"
}

func init() {
	// find the module root
	_, filename, _, _ := runtime.Caller(0)
	moduleRoot = path.Join(path.Dir(filename), "..", "..")
}
