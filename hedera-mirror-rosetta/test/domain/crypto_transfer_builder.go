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

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
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
