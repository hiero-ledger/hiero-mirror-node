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
	"context"
	"database/sql"
	"errors"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	genesisConsensusStartUnset = -1

	// selectLatestWithIndex - Selects the latest record block
	selectLatestWithIndex string = `select consensus_start,
                                           consensus_end,
                                           hash,
                                           index,
                                           prev_hash
                                    from record_file
                                    order by index desc
                                    limit 1`

	// selectByHashWithIndex - Selects the row by given hash
	selectByHashWithIndex string = `select
                                      consensus_start,
                                      coalesce((
                                        select c.consensus_start - 1
                                        from record_file c
                                        where c.index = p.index + 1
                                      ), consensus_end) as consensus_end,
                                      hash,
                                      index,
                                      prev_hash
                                    from record_file p
                                    where hash = @hash collate "C"`

	// selectGenesis - Selects the first block whose consensus_end is after the genesis account balance
	// timestamp. Return the record file with adjusted consensus start
	selectGenesis string = "with" + genesisTimestampCte + `select
                              hash,
                              index,
                              case
                                when genesis.timestamp >= rf.consensus_start then genesis.timestamp + 1
                                else rf.consensus_start
                              end consensus_start,
                              coalesce((
                                select rf1.consensus_start-1
                                from record_file rf1
                                where rf1.index = rf.index + 1
                              ), consensus_end) as consensus_end
                            from record_file rf
                            join genesis
                              on rf.consensus_end > genesis.timestamp
                            order by rf.consensus_end
                            limit 1`

	// selectRecordBlockByIndex - Selects the record block by its index
	selectRecordBlockByIndex string = `select consensus_start,
                                             coalesce((
                                               select consensus_start-1
                                               from record_file
                                               where index = @index + 1::bigint
                                             ), consensus_end) as consensus_end,
                                             hash,
                                             index,
                                             prev_hash
                                      from record_file
                                      where index = @index`
)

type recordBlock struct {
	ConsensusStart int64
	ConsensusEnd   int64
	Hash           string
	Index          int64
	PrevHash       string
}

func (rb *recordBlock) ToBlock(genesisBlock recordBlock) *types.Block {
	consensusStart := rb.ConsensusStart
	parentHash := rb.PrevHash
	parentIndex := rb.Index - 1

	// Handle the edge case for querying genesis block
	if rb.Index == genesisBlock.Index {
		consensusStart = genesisBlock.ConsensusStart
		parentHash = rb.Hash   // Parent hash should be current block hash
		parentIndex = rb.Index // Parent index should be current block index
	}

	return &types.Block{
		Index:               rb.Index,
		Hash:                rb.Hash,
		ParentIndex:         parentIndex,
		ParentHash:          parentHash,
		ConsensusStartNanos: consensusStart,
		ConsensusEndNanos:   rb.ConsensusEnd,
	}
}

// blockRepository struct that has connection to the Database
type blockRepository struct {
	dbClient     interfaces.DbClient
	genesisBlock recordBlock
	once         sync.Once
}

// NewBlockRepository creates an instance of a blockRepository struct
func NewBlockRepository(dbClient interfaces.DbClient) interfaces.BlockRepository {
	return &blockRepository{
		dbClient:     dbClient,
		genesisBlock: recordBlock{ConsensusStart: genesisConsensusStartUnset},
	}
}

func (br *blockRepository) FindByHash(ctx context.Context, hash string) (*types.Block, *rTypes.Error) {
	if hash == "" {
		return nil, hErrors.ErrInvalidArgument
	}

	if err := br.initGenesisRecordFile(ctx); err != nil {
		return nil, err
	}

	return br.findBlockByHash(ctx, hash)
}

func (br *blockRepository) FindByIdentifier(ctx context.Context, index int64, hash string) (
	*types.Block,
	*rTypes.Error,
) {
	if index < 0 || hash == "" {
		return nil, hErrors.ErrInvalidArgument
	}

	if err := br.initGenesisRecordFile(ctx); err != nil {
		return nil, err
	}

	block, err := br.findBlockByHash(ctx, hash)
	if err != nil {
		return nil, err
	}

	if block.Index != index {
		return nil, hErrors.ErrBlockNotFound
	}

	return block, nil
}

func (br *blockRepository) FindByIndex(ctx context.Context, index int64) (*types.Block, *rTypes.Error) {
	if index < 0 {
		return nil, hErrors.ErrInvalidArgument
	}

	if err := br.initGenesisRecordFile(ctx); err != nil {
		return nil, err
	}

	return br.findBlockByIndex(ctx, index)
}

func (br *blockRepository) RetrieveGenesis(ctx context.Context) (*types.Block, *rTypes.Error) {
	if err := br.initGenesisRecordFile(ctx); err != nil {
		return nil, err
	}
	return br.genesisBlock.ToBlock(br.genesisBlock), nil
}

func (br *blockRepository) RetrieveLatest(ctx context.Context) (*types.Block, *rTypes.Error) {
	if err := br.initGenesisRecordFile(ctx); err != nil {
		return nil, err
	}

	db, cancel := br.dbClient.GetDbWithContext(ctx)
	defer cancel()

	rb := &recordBlock{}
	if err := db.Raw(selectLatestWithIndex).First(rb).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrBlockNotFound)
	}

	if rb.Index < br.genesisBlock.Index {
		return nil, hErrors.ErrBlockNotFound
	}

	return rb.ToBlock(br.genesisBlock), nil
}

func (br *blockRepository) findBlockByIndex(ctx context.Context, index int64) (*types.Block, *rTypes.Error) {
	if index < br.genesisBlock.Index {
		return nil, hErrors.ErrBlockNotFound
	}

	db, cancel := br.dbClient.GetDbWithContext(ctx)
	defer cancel()

	rb := &recordBlock{}
	if err := db.Raw(selectRecordBlockByIndex, sql.Named("index", index)).First(rb).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrBlockNotFound)
	}

	return rb.ToBlock(br.genesisBlock), nil
}

func (br *blockRepository) findBlockByHash(ctx context.Context, hash string) (*types.Block, *rTypes.Error) {
	db, cancel := br.dbClient.GetDbWithContext(ctx)
	defer cancel()

	rb := &recordBlock{}
	if err := db.Raw(selectByHashWithIndex, sql.Named("hash", hash)).First(rb).Error; err != nil {
		return nil, handleDatabaseError(err, hErrors.ErrBlockNotFound)
	}

	if rb.Index < br.genesisBlock.Index {
		log.Errorf("The block with hash %s is before the genesis block", hash)
		return nil, hErrors.ErrBlockNotFound
	}

	return rb.ToBlock(br.genesisBlock), nil
}

func (br *blockRepository) initGenesisRecordFile(ctx context.Context) *rTypes.Error {
	if br.genesisBlock.ConsensusStart != genesisConsensusStartUnset {
		return nil
	}

	db, cancel := br.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var rb recordBlock
	if err := db.Raw(selectGenesis).First(&rb).Error; err != nil {
		return handleDatabaseError(err, hErrors.ErrNodeIsStarting)
	}

	br.once.Do(func() {
		br.genesisBlock = rb
	})

	log.Infof("Fetched genesis record file, index - %d", br.genesisBlock.Index)
	return nil
}

func handleDatabaseError(err error, recordNotFoundErr *rTypes.Error) *rTypes.Error {
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return recordNotFoundErr
	}

	log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
	return hErrors.ErrDatabaseError
}
