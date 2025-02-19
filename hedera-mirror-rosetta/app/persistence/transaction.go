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
	"encoding/json"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/tools"
	log "github.com/sirupsen/logrus"
)

const (
	batchSize                                                 = 2000
	transactionResultFeeScheduleFilePartUploaded        int32 = 104
	transactionResultSuccess                            int32 = 22
	transactionResultSuccessButMissingExpectedOperation int32 = 220
)

const (
	andTransactionHashFilter  = " and transaction_hash = @hash"
	orderByConsensusTimestamp = " order by consensus_timestamp"
	// selectTransactionsInTimestampRange selects the transactions with its crypto transfers in json.
	selectTransactionsInTimestampRange = "with" + genesisTimestampCte + `select
                                            t.consensus_timestamp,
                                            t.entity_id,
                                            t.itemized_transfer,
                                            t.memo,
                                            t.payer_account_id,
                                            t.result,
                                            t.transaction_hash as hash,
                                            t.type,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', entity_id,
                                                'amount', amount) order by entity_id)
                                              from crypto_transfer
                                              where consensus_timestamp = t.consensus_timestamp and
                                                (errata is null or errata <> 'DELETE')
                                            ), '[]') as crypto_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', account_id,
                                                'amount', amount) order by account_id)
                                              from staking_reward_transfer
                                              where consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as staking_reward_payouts
                                          from transaction t
                                          where consensus_timestamp >= @start and consensus_timestamp <= @end`
	selectTransactionsByHashInTimestampRange  = selectTransactionsInTimestampRange + andTransactionHashFilter
	selectTransactionsInTimestampRangeOrdered = selectTransactionsInTimestampRange + orderByConsensusTimestamp
)

var stakingRewardAccountId = domain.MustDecodeEntityId(800)

// transaction maps to the transaction query which returns the required transaction fields, CryptoTransfers json string,
// and itemizedTransfers json string.
type transaction struct {
	ConsensusTimestamp   int64
	CryptoTransfers      string
	EntityId             *domain.EntityId
	Hash                 []byte
	ItemizedTransfer     domain.ItemizedTransferSlice `gorm:"type:jsonb"`
	Memo                 []byte
	PayerAccountId       domain.EntityId
	Result               int16
	StakingRewardPayouts string
	Type                 int16
}

func (t transaction) getHashString() string {
	return tools.SafeAddHexPrefix(hex.EncodeToString(t.Hash))
}

type transfer interface {
	getAccountId() domain.EntityId
	getAmount() types.Amount
}

type hbarTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
}

func (t hbarTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t hbarTransfer) getAmount() types.Amount {
	return &types.HbarAmount{Value: t.Amount}
}

// transactionRepository struct that has connection to the Database
type transactionRepository struct {
	once     sync.Once
	dbClient interfaces.DbClient
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient interfaces.DbClient) interfaces.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

func (tr *transactionRepository) FindBetween(ctx context.Context, start, end int64) (
	[]*types.Transaction,
	*rTypes.Error,
) {
	if start > end {
		return nil, hErrors.ErrStartMustNotBeAfterEnd
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	transactions := make([]*transaction, 0)
	for start <= end {
		transactionsBatch := make([]*transaction, 0)
		err := db.
			Raw(
				selectTransactionsInTimestampRangeOrdered,
				sql.Named("start", start),
				sql.Named("end", end),
			).
			Limit(batchSize).
			Find(&transactionsBatch).
			Error
		if err != nil {
			log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
			return nil, hErrors.ErrDatabaseError
		}

		transactions = append(transactions, transactionsBatch...)

		if len(transactionsBatch) < batchSize {
			break
		}

		start = transactionsBatch[len(transactionsBatch)-1].ConsensusTimestamp + 1
	}

	hashes := make([]string, 0)
	sameHashMap := make(map[string][]*transaction)
	for _, t := range transactions {
		h := t.getHashString()
		if _, ok := sameHashMap[h]; !ok {
			// save the unique hashes in chronological order
			hashes = append(hashes, h)
		}

		sameHashMap[h] = append(sameHashMap[h], t)
	}

	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, hash := range hashes {
		sameHashTransactions := sameHashMap[hash]
		transaction, err := tr.constructTransaction(sameHashTransactions)
		if err != nil {
			return nil, err
		}
		res = append(res, transaction)
	}
	return res, nil
}

func (tr *transactionRepository) FindByHashInBlock(
	ctx context.Context,
	hashStr string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []*transaction
	transactionHash, err := hex.DecodeString(tools.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, hErrors.ErrInvalidTransactionIdentifier
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	if err = db.Raw(
		selectTransactionsByHashInTimestampRange,
		sql.Named("hash", transactionHash),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).Find(&transactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	if len(transactions) == 0 {
		return nil, hErrors.ErrTransactionNotFound
	}

	transaction, rErr := tr.constructTransaction(transactions)
	if rErr != nil {
		return nil, rErr
	}

	return transaction, nil
}

func (tr *transactionRepository) constructTransaction(sameHashTransactions []*transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	allFailed := true
	firstTransaction := sameHashTransactions[0]
	operations := make(types.OperationSlice, 0)
	result := &types.Transaction{Hash: firstTransaction.getHashString(), Memo: firstTransaction.Memo}
	success := types.GetTransactionResult(transactionResultSuccess)
	transactionType := types.GetTransactionType(int32(firstTransaction.Type))

	for _, transaction := range sameHashTransactions {
		cryptoTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.CryptoTransfers), &cryptoTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		stakingRewardPayouts := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.StakingRewardPayouts), &stakingRewardPayouts); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		var feeHbarTransfers []hbarTransfer
		var itemizedTransfers []hbarTransfer
		var stakingRewardTransfers []hbarTransfer
		feeHbarTransfers, itemizedTransfers, stakingRewardTransfers = categorizeHbarTransfers(
			cryptoTransfers,
			transaction.ItemizedTransfer,
			stakingRewardPayouts,
		)

		transactionResult := types.GetTransactionResult(int32(transaction.Result))

		operations = tr.appendHbarTransferOperations(transactionResult, transactionType, itemizedTransfers, operations)
		// fee transfers are always successful regardless of the transaction result
		operations = tr.appendHbarTransferOperations(success, types.OperationTypeFee, feeHbarTransfers, operations)
		// staking reward transfers (both credit and debit) are always successful and marked as crypto transfer
		operations = tr.appendHbarTransferOperations(success, types.OperationTypeCryptoTransfer,
			stakingRewardTransfers, operations)

		if IsTransactionResultSuccessful(int32(transaction.Result)) {
			allFailed = false
			result.EntityId = transaction.EntityId
		}
	}

	if allFailed {
		// add an 0-amount hbar transfer with the failed status to indicate the transaction has failed
		operations = tr.appendHbarTransferOperations(
			types.GetTransactionResult(int32(firstTransaction.Result)),
			transactionType,
			[]hbarTransfer{{AccountId: firstTransaction.PayerAccountId}},
			operations,
		)
	}

	result.Operations = operations
	return result, nil
}

func (tr *transactionRepository) appendHbarTransferOperations(
	transactionResult string,
	operationType string,
	hbarTransfers []hbarTransfer,
	operations types.OperationSlice,
) types.OperationSlice {
	transfers := make([]transfer, 0, len(hbarTransfers))
	for _, hbarTransfer := range hbarTransfers {
		transfers = append(transfers, hbarTransfer)
	}

	return tr.appendTransferOperations(transactionResult, operationType, transfers, operations)
}

func (tr *transactionRepository) appendTransferOperations(
	transactionResult string,
	operationType string,
	transfers []transfer,
	operations types.OperationSlice,
) types.OperationSlice {
	for _, transfer := range transfers {
		operations = append(operations, types.Operation{
			AccountId: types.NewAccountIdFromEntityId(transfer.getAccountId()),
			Amount:    transfer.getAmount(),
			Index:     int64(len(operations)),
			Status:    transactionResult,
			Type:      operationType,
		})
	}
	return operations
}

func categorizeHbarTransfers(hbarTransfers []hbarTransfer, itemizedTransfer domain.ItemizedTransferSlice, stakingRewardPayouts []hbarTransfer) (
	feeHbarTransfers, adjustedItemizedTransfers, stakingRewardTransfers []hbarTransfer,
) {
	entityIds := make(map[int64]struct{})
	for _, transfer := range hbarTransfers {
		entityIds[transfer.AccountId.EncodedId] = struct{}{}
	}

	adjustedItemizedTransfers = make([]hbarTransfer, 0, len(itemizedTransfer))
	for _, transfer := range itemizedTransfer {
		entityId := transfer.EntityId.EncodedId
		// skip itemized transfer whose entity id is not in the transaction record's transfer list. One exception is
		// we always add itemized transfers to the staking reward account if there are staking reward payouts
		_, exists := entityIds[entityId]
		if exists || (entityId == stakingRewardAccountId.EncodedId && len(stakingRewardPayouts) != 0) {
			adjustedItemizedTransfers = append(adjustedItemizedTransfers, hbarTransfer{
				AccountId: transfer.EntityId,
				Amount:    transfer.Amount,
			})
		}
	}

	// add a crypto transfer which debits the total payout from account 0.0.800 if any
	rewardPayoutTotal := int64(0)
	stakingRewardTransfers = make([]hbarTransfer, 0)
	for _, stakingRewardPayout := range stakingRewardPayouts {
		rewardPayoutTotal -= stakingRewardPayout.Amount
		stakingRewardTransfers = append(stakingRewardTransfers, stakingRewardPayout)
	}

	if rewardPayoutTotal != 0 {
		stakingRewardTransfers = append(stakingRewardTransfers, hbarTransfer{
			AccountId: stakingRewardAccountId,
			Amount:    rewardPayoutTotal,
		})
	}

	itemizedTransferMap := aggregateHbarTransfers(itemizedTransfer, stakingRewardTransfers)
	return getFeeHbarTransfers(hbarTransfers, itemizedTransferMap), adjustedItemizedTransfers, stakingRewardTransfers
}

func IsTransactionResultSuccessful(result int32) bool {
	return result == transactionResultFeeScheduleFilePartUploaded ||
		result == transactionResultSuccess ||
		result == transactionResultSuccessButMissingExpectedOperation
}

func aggregateHbarTransfers(itemTransfer domain.ItemizedTransferSlice, hbarTransfers []hbarTransfer) map[int64]int64 {
	// aggregate transfers for the same entity id
	transferMap := make(map[int64]int64)

	for _, transfer := range itemTransfer {
		transferMap[transfer.EntityId.EncodedId] += transfer.Amount
	}

	for _, transfer := range hbarTransfers {
		transferMap[transfer.AccountId.EncodedId] += transfer.Amount
	}

	return transferMap
}

func getFeeHbarTransfers(cryptoTransfers []hbarTransfer, itemizedTransferMap map[int64]int64) []hbarTransfer {
	cryptoTransferMap := make(map[int64]hbarTransfer)
	accountIds := make([]int64, 0)
	for _, transfer := range cryptoTransfers {
		accountId := transfer.AccountId.EncodedId
		if _, ok := cryptoTransferMap[accountId]; !ok {
			accountIds = append(accountIds, accountId)
		}
		cryptoTransferMap[accountId] = hbarTransfer{
			AccountId: transfer.AccountId,
			Amount:    transfer.Amount + cryptoTransferMap[accountId].Amount,
		}
	}

	adjusted := make([]hbarTransfer, 0, len(cryptoTransfers))
	for _, accountId := range accountIds {
		aggregated := cryptoTransferMap[accountId]
		amount := aggregated.Amount - itemizedTransferMap[accountId]
		if amount != 0 {
			adjusted = append(adjusted, hbarTransfer{
				AccountId: aggregated.AccountId,
				Amount:    amount,
			})
		}
	}

	return adjusted
}
