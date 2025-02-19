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
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
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
