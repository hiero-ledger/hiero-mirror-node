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
	"math"
	"testing"

	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const genesisBlockIndex int64 = 3

var (
	accountBalances = []*domain.AccountBalance{
		// From the first account balance snapshot
		{
			AccountId:          treasuryEntityId,
			Balance:            5_000_000_000,
			ConsensusTimestamp: 90,
		},
		{
			AccountId:          domain.MustDecodeEntityId(3),
			Balance:            1_000_000_000,
			ConsensusTimestamp: 90,
		},
		// From the second account balance snapshot
		{
			AccountId:          treasuryEntityId,
			Balance:            5_000_000_000,
			ConsensusTimestamp: 10000,
		},
		{
			AccountId:          domain.MustDecodeEntityId(3),
			Balance:            1_000_000_000,
			ConsensusTimestamp: 10000,
		},
		// From the third account balance snapshot
		{
			AccountId:          treasuryEntityId,
			Balance:            5_000_000_000,
			ConsensusTimestamp: 20000,
		},
		{
			AccountId:          domain.MustDecodeEntityId(3),
			Balance:            1_000_000_000,
			ConsensusTimestamp: 20000,
		},
	}
	expectedGenesisBlock = &types.Block{
		ConsensusStartNanos: 91,
		ConsensusEndNanos:   109,
		Hash:                "genesis_record_file_hash",
		Index:               genesisBlockIndex,
		ParentHash:          "genesis_record_file_hash",
		ParentIndex:         genesisBlockIndex,
	}
	expectedSecondBlock = &types.Block{
		ConsensusStartNanos: 110,
		ConsensusEndNanos:   129,
		Hash:                "second_record_file_hash",
		Index:               genesisBlockIndex + 1,
		ParentHash:          "genesis_record_file_hash",
		ParentIndex:         genesisBlockIndex,
	}
	expectedThirdBlock = &types.Block{
		ConsensusStartNanos: 130,
		ConsensusEndNanos:   145,
		Hash:                "third_record_file_hash",
		Index:               genesisBlockIndex + 2,
		ParentHash:          "second_record_file_hash",
		ParentIndex:         genesisBlockIndex + 1,
	}
	nodeEntityId = domain.MustDecodeEntityId(3)
	recordFiles  = []*domain.RecordFile{
		{
			ConsensusStart: 80,
			ConsensusEnd:   100,
			Hash:           "genesis_record_file_hash",
			Index:          genesisBlockIndex,
			Name:           "genesis_record_file",
			PrevHash:       "previous_record_file_hash",
		},
		{
			ConsensusStart: 110,
			ConsensusEnd:   120,
			Hash:           "second_record_file_hash",
			Index:          genesisBlockIndex + 1,
			Name:           "second_record_file",
			PrevHash:       "genesis_record_file_hash",
		},
		{
			ConsensusStart: 130,
			ConsensusEnd:   145,
			Hash:           "third_record_file_hash",
			Index:          genesisBlockIndex + 2,
			Name:           "third_record_file",
			PrevHash:       "second_record_file_hash",
		},
	}
	genesisRecordFile       = recordFiles[0]
	recordFileBeforeGenesis = &domain.RecordFile{
		ConsensusStart: 50,
		ConsensusEnd:   79,
		Hash:           "previous_record_file_hash",
		Index:          genesisBlockIndex - 1,
		Name:           "previous_record_file",
		PrevHash:       "some_hash",
	}
)

// run the suite
func TestBlockRepositorySuite(t *testing.T) {
	suite.Run(t, new(blockRepositorySuite))
}

type blockRepositorySuite struct {
	integrationTest
	suite.Suite
}

func (suite *blockRepositorySuite) SetupTest() {
	suite.integrationTest.SetupTest()
	db.CreateDbRecords(dbClient, accountBalances, recordFiles, recordFileBeforeGenesis)
}

func (suite *blockRepositorySuite) TestFindByHashGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByHashNonGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, expectedSecondBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByHashBlockBeforeGenesis() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, recordFileBeforeGenesis.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashNoRecordFile() {
	// given
	truncateTables(domain.RecordFile{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashEmptyHash() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, "")

	// then
	assert.Equal(suite.T(), errors.ErrInvalidArgument, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByHashDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.FindByHash(defaultContext, genesisRecordFile.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, genesisBlockIndex, expectedGenesisBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNonGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, expectedSecondBlock.Index, expectedSecondBlock.Hash)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierBlockBeforeGenesis() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, recordFileBeforeGenesis.Index, recordFileBeforeGenesis.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierInvalidArgument() {
	// given
	tests := []struct {
		name  string
		index int64
		hash  string
	}{
		{
			"negative index",
			-1,
			"hash",
		},
		{
			"empty hash",
			0,
			"",
		},
	}
	repo := NewBlockRepository(dbClient)

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// when
			actual, err := repo.FindByIdentifier(defaultContext, tt.index, tt.hash)

			// then
			assert.Equal(t, errors.ErrInvalidArgument, err)
			assert.Nil(t, actual)
		})
	}
}

func (suite *blockRepositorySuite) TestFindByIdentifierIndexHashMismatch() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, expectedGenesisBlock.Index, expectedSecondBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNoRecordFile() {
	// given
	truncateTables(domain.RecordFile{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierNotFound() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 1000, "foobar")

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIdentifierDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.FindByIdentifier(defaultContext, 0, expectedGenesisBlock.Hash)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, genesisBlockIndex)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNonGenesisBlock() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, expectedSecondBlock.Index)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedSecondBlock, actual)
}

func (suite *blockRepositorySuite) TestFindByIndexBlockBeforeGenesis() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, recordFileBeforeGenesis.Index)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexInvalidIndex() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, -1)

	// then
	assert.Equal(suite.T(), errors.ErrInvalidArgument, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFineByIndexNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNoRecordFile() {
	// given
	truncateTables(domain.RecordFile{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexNotFound() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 1000)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestFindByIndexDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.FindByIndex(defaultContext, 0)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesis() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)

	// given blocks are deleted
	truncateTables(domain.RecordFile{})

	// when
	actual, err = repo.RetrieveGenesis(defaultContext)

	// then RetrieveGenesis returns the cached block info
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedGenesisBlock, actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisIndexOverflowInt4() {
	genesisIndexes := []int64{int64(math.MaxInt32) + 1, int64(math.MinInt32) - 1}
	for _, genesisIndex := range genesisIndexes {
		suite.T().Run(fmt.Sprintf("%d", genesisIndex), func(t *testing.T) {
			// given
			truncateTables(domain.RecordFile{})
			recordFile1 := *recordFiles[0]
			recordFile1.Index = genesisIndex
			recordFile2 := *recordFiles[1]
			recordFile2.Index = genesisIndex + 1
			db.CreateDbRecords(dbClient, []*domain.RecordFile{&recordFile1, &recordFile2})
			expected := *expectedGenesisBlock
			expected.Index = genesisIndex
			expected.ParentIndex = genesisIndex
			repo := NewBlockRepository(dbClient)

			// when
			actual, err := repo.RetrieveGenesis(defaultContext)

			// then
			assert.Nil(suite.T(), err)
			assert.Equal(suite.T(), &expected, actual)
		})
	}
}

func (suite *blockRepositorySuite) TestRetrieveGenesisNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisNoRecordFile() {
	// given
	truncateTables(domain.RecordFile{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveGenesisDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveGenesis(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestNonGenesisBlock() {
	// given
	expected := expectedThirdBlock
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestWithOnlyGenesisBlock() {
	// given
	truncateTables(domain.RecordFile{})
	db.CreateDbRecords(dbClient, genesisRecordFile)
	repo := NewBlockRepository(dbClient)
	expected := *expectedGenesisBlock
	expected.ConsensusEndNanos = recordFiles[0].ConsensusEnd

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), &expected, actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestWithBlockBeforeGenesis() {
	// given
	truncateTables(domain.RecordFile{})
	db.CreateDbRecords(dbClient, recordFileBeforeGenesis)
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestNoAccountBalance() {
	// given
	truncateTables(domain.AccountBalance{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestNoRecordFile() {
	// given
	truncateTables(domain.RecordFile{})
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrNodeIsStarting, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestRecordFileTableInconsistent() {
	// given
	repo := NewBlockRepository(dbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expectedThirdBlock, actual)

	// when
	truncateTables(domain.RecordFile{})
	actual, err = repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)

	// when
	db.CreateDbRecords(dbClient, recordFileBeforeGenesis)
	actual, err = repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrBlockNotFound, err)
	assert.Nil(suite.T(), actual)
}

func (suite *blockRepositorySuite) TestRetrieveLatestDbConnectionError() {
	// given
	repo := NewBlockRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveLatest(defaultContext)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func TestRecordFileToBlock(t *testing.T) {
	genesisBlock := recordBlock{
		ConsensusStart: 110,
		ConsensusEnd:   200,
		Hash:           "hash",
		Index:          genesisBlockIndex,
		PrevHash:       "prev_hash",
	}
	tests := []struct {
		name     string
		input    recordBlock
		expected *types.Block
	}{
		{
			"genesis block",
			recordBlock{
				ConsensusStart: 100,
				ConsensusEnd:   200,
				Hash:           "hash",
				Index:          genesisBlockIndex,
				PrevHash:       "prev_hash",
			},
			&types.Block{
				Index:               genesisBlockIndex,
				Hash:                "hash",
				ParentIndex:         genesisBlockIndex,
				ParentHash:          "hash",
				ConsensusStartNanos: genesisBlock.ConsensusStart,
				ConsensusEndNanos:   200,
			},
		},
		{
			"non-genesis block",
			recordBlock{
				ConsensusStart: 201,
				ConsensusEnd:   300,
				Hash:           "hash",
				Index:          8,
				PrevHash:       "prev_hash",
			},
			&types.Block{
				Index:               8,
				Hash:                "hash",
				ParentIndex:         7,
				ParentHash:          "prev_hash",
				ConsensusStartNanos: 201,
				ConsensusEndNanos:   300,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.ToBlock(genesisBlock))
		})
	}
}
