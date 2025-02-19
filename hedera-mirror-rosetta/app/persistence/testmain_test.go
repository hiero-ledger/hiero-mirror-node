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

package persistence

import (
	"fmt"
	"gorm.io/gorm/schema"
	"os"
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/db"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	tdb "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/db"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var (
	dbResource      tdb.DbResource
	dbClient        interfaces.DbClient
	invalidDbClient interfaces.DbClient
)

type integrationTest struct{}

func (*integrationTest) SetupTest() {
	tdb.CleanupDb(dbResource.GetDb())
}

func addTransaction(
	dbClient interfaces.DbClient,
	consensusTimestamp int64,
	entityId *domain.EntityId,
	nodeAccountId *domain.EntityId,
	payerAccountId domain.EntityId,
	result int16,
	transactionHash []byte,
	transactionType int16,
	validStartNs int64,
	cryptoTransfers []domain.CryptoTransfer,
	itemizedTransfer domain.ItemizedTransferSlice,
	memo []byte,
) {
	tx := &domain.Transaction{
		ConsensusTimestamp:   consensusTimestamp,
		ChargedTxFee:         17,
		EntityId:             entityId,
		ItemizedTransfer:     itemizedTransfer,
		Memo:                 memo,
		NodeAccountId:        nodeAccountId,
		Nonce:                0,
		PayerAccountId:       payerAccountId,
		Result:               result,
		TransactionHash:      transactionHash,
		Type:                 transactionType,
		ValidDurationSeconds: 120,
		ValidStartNs:         validStartNs,
	}
	tdb.CreateDbRecords(dbClient, tx)

	if len(cryptoTransfers) != 0 {
		tdb.CreateDbRecords(dbClient, cryptoTransfers)
	}
}

func setup() {
	dbResource = tdb.SetupDb(true)
	dbClient = db.NewDbClient(dbResource.GetGormDb(), 0)

	config := dbResource.GetDbConfig()
	config.Password = "bad_password"
	invalid, _ := gorm.Open(postgres.Open(config.GetDsn()), &gorm.Config{Logger: logger.Discard})
	invalidDbClient = db.NewDbClient(invalid, 0)
}

func teardown() {
	tdb.TearDownDb(dbResource)
}

func truncateTables(tables ...schema.Tabler) {
	sqls := make([]string, 0)
	for _, table := range tables {
		sqls = append(sqls, fmt.Sprintf("truncate %s", table.TableName()))
	}
	tdb.ExecSql(dbClient, sqls...)
}

func TestMain(m *testing.M) {
	code := 0

	setup()
	defer func() {
		teardown()
		os.Exit(code)
	}()

	code = m.Run()
}
