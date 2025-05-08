// SPDX-License-Identifier: Apache-2.0

package domain

const (
	ErrataTypeDelete = "DELETE"
	ErrataTypeInsert = "INSERT"

	cryptoTransferTableName = "crypto_transfer"
)

type CryptoTransfer struct {
	Amount             int64
	ConsensusTimestamp int64
	EntityId           EntityId
	Errata             *string
	PayerAccountId     EntityId
}

func (CryptoTransfer) TableName() string {
	return cryptoTransferTableName
}
