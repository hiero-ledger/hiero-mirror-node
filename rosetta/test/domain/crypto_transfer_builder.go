// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
)

var defaultPayer = domain.MustDecodeEntityId(211)

type CryptoTransferBuilder struct {
	dbClient       interfaces.DbClient
	cryptoTransfer domain.CryptoTransfer
}

func (b *CryptoTransferBuilder) Amount(amount int64) *CryptoTransferBuilder {
	b.cryptoTransfer.Amount = amount
	return b
}

func (b *CryptoTransferBuilder) EntityId(entityId int64) *CryptoTransferBuilder {
	b.cryptoTransfer.EntityId = domain.MustDecodeEntityId(entityId)
	return b
}

func (b *CryptoTransferBuilder) Errata(errata string) *CryptoTransferBuilder {
	b.cryptoTransfer.Errata = &errata
	return b
}

func (b *CryptoTransferBuilder) Timestamp(timestamp int64) *CryptoTransferBuilder {
	b.cryptoTransfer.ConsensusTimestamp = timestamp
	return b
}

func (b *CryptoTransferBuilder) Persist() {
	b.dbClient.GetDb().Create(&b.cryptoTransfer)
}

func NewCryptoTransferBuilder(dbClient interfaces.DbClient) *CryptoTransferBuilder {
	return &CryptoTransferBuilder{
		dbClient:       dbClient,
		cryptoTransfer: domain.CryptoTransfer{PayerAccountId: defaultPayer},
	}
}
