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
	"encoding/hex"
	"errors"
	"fmt"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	log "github.com/sirupsen/logrus"
	"gorm.io/gorm"
)

const (
	balanceChangeBetween = "with" + genesisTimestampCte + `select
                              coalesce((
                                select sum(amount) from crypto_transfer
                                where
                                  consensus_timestamp > @start and
                                  consensus_timestamp <= @end and
                                  entity_id = @account_id and
                                  (errata is null or errata <> 'DELETE')
                              ), 0) as value`
	latestBalanceBeforeConsensus = `select
                                      bt.consensus_timestamp,
                                      coalesce((
                                        select balance
                                        from account_balance as ab
                                        where account_id = @account_id and
                                          ab.consensus_timestamp <= bt.consensus_timestamp and
                                          ab.consensus_timestamp >= @lower_bound and
                                          ab.consensus_timestamp <= @timestamp
                                        order by ab.consensus_timestamp desc
                                        limit 1
                                      ), 0) as balance
                                    from (
                                      select consensus_timestamp
                                      from account_balance
                                      where account_id = 2 and
                                        consensus_timestamp >= @lower_bound and
                                        consensus_timestamp <= @timestamp
                                      order by consensus_timestamp desc
                                      limit 1
                                    ) as bt`
	selectCryptoEntityWithAliasById = "select alias, id from entity where id = @id"
	selectCryptoEntityByAlias       = `select id, deleted, timestamp_range
                                 from entity
                                 where alias = @alias and timestamp_range @> @consensus_end
                                 union all
                                 select id, deleted, timestamp_range
                                 from entity_history
                                 where alias = @alias and timestamp_range @> @consensus_end
                                 order by timestamp_range desc`
	selectCurrentCryptoEntityByAlias = `select id from entity
                                 where alias = @alias and (deleted is null or deleted is false)`
	selectCryptoEntityById = `select id, deleted, timestamp_range
                              from entity
                              where type in ('ACCOUNT', 'CONTRACT') and id = @id`
	// select the lower bound of the second last partition whose lower bound is LTE @timestamp. It's possible that
	// @timestamp is in a partition for which the first account balance snapshot is yet to be filled, thus the need
	// to look back one more partition for account balance
	selectPreviousPartitionLowerBound = `with last_two as (
                                select *
                                from mirror_node_time_partitions
                                where parent = 'account_balance' and @timestamp >= from_timestamp
                                order by from_timestamp desc
                                limit 2
                              )
                              select from_timestamp
                              from last_two
                              order by from_timestamp
                              limit 1`
)

type accountBalanceChange struct {
	Value int64
}

type accountBalance struct {
	ConsensusTimestamp int64
	Balance            int64
}

// accountRepository struct that has connection to the Database
type accountRepository struct {
	dbClient interfaces.DbClient
}

// NewAccountRepository creates an instance of a accountRepository struct
func NewAccountRepository(dbClient interfaces.DbClient) interfaces.AccountRepository {
	return &accountRepository{dbClient}
}

func (ar *accountRepository) GetAccountAlias(ctx context.Context, accountId types.AccountId) (
	zero types.AccountId,
	_ *rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var entity domain.Entity
	if err := db.Raw(selectCryptoEntityWithAliasById, sql.Named("id", accountId.GetId())).First(&entity).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return accountId, nil
		}

		return zero, hErrors.ErrDatabaseError
	}

	if len(entity.Alias) == 0 {
		return accountId, nil
	}

	accountAlias, err := types.NewAccountIdFromEntity(entity)
	if err != nil {
		log.Warnf("Failed to create AccountId from alias '0x%s': %v", hex.EncodeToString(entity.Alias), err)
		return accountId, nil
	}

	return accountAlias, nil
}

func (ar *accountRepository) GetAccountId(ctx context.Context, accountId types.AccountId) (
	zero types.AccountId,
	_ *rTypes.Error,
) {
	if !accountId.HasAlias() {
		return accountId, nil
	}

	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var entity domain.Entity
	if err := db.Raw(selectCurrentCryptoEntityByAlias, sql.Named("alias", accountId.GetAlias())).First(&entity).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return zero, hErrors.ErrAccountNotFound
		}

		return zero, hErrors.ErrDatabaseError
	}

	return types.NewAccountIdFromEntityId(entity.Id), nil
}

func (ar *accountRepository) RetrieveBalanceAtBlock(
	ctx context.Context,
	accountId types.AccountId,
	consensusEnd int64,
) (types.AmountSlice, string, *rTypes.Error) {
	var entityIdString string
	entity, err := ar.getCryptoEntity(ctx, accountId, consensusEnd)
	if err != nil {
		return nil, entityIdString, err
	}

	if entity == nil && accountId.HasAlias() {
		return types.AmountSlice{&types.HbarAmount{}}, entityIdString, nil
	}

	balanceChangeEndTimestamp := consensusEnd
	balanceSnapshotEndTimestamp := consensusEnd
	if entity != nil && entity.Deleted != nil && *entity.Deleted && entity.GetModifiedTimestamp() <= consensusEnd {
		// if an account / contract is deleted at t1, a balance snapshot at t1 (if exists) won't have info for the
		// entity, thus look for a balance snapshot at or before the deleted timestamp - 1
		// however, the balanceChangeEndTimestamp should be the deletion timestamp since the crypto delete transaction
		// may have a transfer which moves the remaining hbar balance to another account
		balanceChangeEndTimestamp = entity.GetModifiedTimestamp()
		balanceSnapshotEndTimestamp = balanceChangeEndTimestamp - 1
	}

	id := accountId.GetId()
	if accountId.HasAlias() {
		// entity can't be nil if accountId has alias
		id = entity.Id.EncodedId
	}
	snapshotTimestamp, hbarAmount, err := ar.getLatestBalanceSnapshot(
		ctx,
		id,
		balanceSnapshotEndTimestamp,
	)
	if err != nil {
		return nil, entityIdString, err
	}

	hbarValue, err := ar.getBalanceChange(
		ctx,
		id,
		snapshotTimestamp,
		balanceChangeEndTimestamp,
	)
	if err != nil {
		return nil, entityIdString, err
	}

	hbarAmount.Value += hbarValue

	amounts := make(types.AmountSlice, 0, 1)
	amounts = append(amounts, hbarAmount)

	if entity != nil {
		// return the entity id string in the format of 'shard.realm.num'
		entityIdString = entity.Id.String()
	}
	return amounts, entityIdString, nil
}

func (ar *accountRepository) getCryptoEntity(ctx context.Context, accountId types.AccountId, consensusEnd int64) (
	*domain.Entity,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var query string
	var args []interface{}
	if accountId.HasAlias() {
		query = selectCryptoEntityByAlias
		args = []interface{}{
			sql.Named("alias", accountId.GetAlias()),
			sql.Named("consensus_end", getInclusiveInt8Range(consensusEnd, consensusEnd)),
		}
	} else {
		query = selectCryptoEntityById
		args = []interface{}{sql.Named("id", accountId.GetId())}
	}

	entities := make([]domain.Entity, 0)
	if err := db.Raw(query, args...).Scan(&entities).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for entity %s", err, accountId),
		)
		return nil, hErrors.ErrDatabaseError
	}

	if len(entities) == 0 {
		return nil, nil
	}

	// if it's by alias, return the first match which is the current one owns the alias, even though it may be deleted
	return &entities[0], nil
}

func (ar *accountRepository) getLatestBalanceSnapshot(ctx context.Context, accountId, timestamp int64) (
	int64,
	*types.HbarAmount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var partitionLowerBound int64
	if err := db.Raw(
		selectPreviousPartitionLowerBound,
		sql.Named("timestamp", timestamp),
	).Scan(&partitionLowerBound).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for previous account_balance partition before %d", err, timestamp),
		)
	}

	// gets the most recent balance at or before timestamp
	ab := &accountBalance{}
	if err := db.Raw(
		latestBalanceBeforeConsensus,
		sql.Named("account_id", accountId),
		sql.Named("lower_bound", partitionLowerBound),
		sql.Named("timestamp", timestamp),
	).First(ab).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for account %d's balance at or before %d", err, accountId, timestamp),
		)
		return 0, nil, hErrors.ErrDatabaseError
	}

	if ab.ConsensusTimestamp == 0 {
		return 0, nil, hErrors.ErrNodeIsStarting
	}

	hbarAmount := types.HbarAmount{Value: ab.Balance}

	return ab.ConsensusTimestamp, &hbarAmount, nil
}

func (ar *accountRepository) getBalanceChange(ctx context.Context, accountId, consensusStart, consensusEnd int64) (
	int64,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	change := &accountBalanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	if err := db.Raw(
		balanceChangeBetween,
		sql.Named("account_id", accountId),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
		sql.Named("end_range", getInclusiveInt8Range(consensusEnd, consensusEnd)),
	).First(change).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for account %d's balance change in [%d, %d]", err, accountId, consensusStart,
				consensusEnd),
		)
		return 0, hErrors.ErrDatabaseError
	}

	return change.Value, nil
}
