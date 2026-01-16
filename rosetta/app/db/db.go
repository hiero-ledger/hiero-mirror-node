// SPDX-License-Identifier: Apache-2.0

package db

import (
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	gormlogrus "github.com/onrik/gorm-logrus"
	log "github.com/sirupsen/logrus"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

// ConnectToDb establishes connection to the Postgres Database
func ConnectToDb(dbConfig config.Db) interfaces.DbClient {
	db, err := gorm.Open(postgres.Open(dbConfig.GetDsn()), &gorm.Config{Logger: gormlogrus.New()})
	if err != nil {
		log.Warn(err)
	} else {
		log.Info("Successfully connected to database")
	}

	sqlDb, err := db.DB()
	if err != nil {
		log.Errorf("Failed to get sql DB: %s", err)
		return nil
	}

	sqlDb.SetMaxIdleConns(dbConfig.Pool.MaxIdleConnections)
	sqlDb.SetConnMaxLifetime(time.Duration(dbConfig.Pool.MaxLifetime) * time.Minute)
	sqlDb.SetMaxOpenConns(dbConfig.Pool.MaxOpenConnections)

	return NewDbClient(db, dbConfig.StatementTimeout)
}
