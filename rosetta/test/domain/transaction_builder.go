// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"github.com/thanhpk/randstr"
)

type TransactionBuilder struct {
	dbClient    interfaces.DbClient
	transaction domain.Transaction
}

func (b *TransactionBuilder) ConsensusTimestamp(timestamp int64) *TransactionBuilder {
	b.transaction.ConsensusTimestamp = timestamp
	return b
}

func (b *TransactionBuilder) EntityId(encodedId int64) *TransactionBuilder {
	entityId := domain.MustDecodeEntityId(encodedId)
	b.transaction.EntityId = &entityId
	return b
}

func (b *TransactionBuilder) ItemizedTransfer(itemizedTransfer domain.ItemizedTransferSlice) *TransactionBuilder {
	b.transaction.ItemizedTransfer = itemizedTransfer
	return b
}

func (b *TransactionBuilder) Result(result int16) *TransactionBuilder {
	b.transaction.Result = result
	return b
}

func (b *TransactionBuilder) Type(txnType int16) *TransactionBuilder {
	b.transaction.Type = txnType
	return b
}

func (b *TransactionBuilder) Persist() domain.Transaction {
	b.dbClient.GetDb().Create(&b.transaction)
	return b.transaction
}

func NewTransactionBuilder(dbClient interfaces.DbClient, payer, validStartNs int64) *TransactionBuilder {
	transaction := domain.Transaction{
		ConsensusTimestamp:   validStartNs + 1,
		PayerAccountId:       domain.MustDecodeEntityId(payer),
		Result:               22,
		TransactionHash:      randstr.Bytes(8),
		Type:                 domain.TransactionTypeCryptoTransfer,
		ValidDurationSeconds: 120,
		ValidStartNs:         validStartNs,
	}
	return &TransactionBuilder{dbClient: dbClient, transaction: transaction}
}
