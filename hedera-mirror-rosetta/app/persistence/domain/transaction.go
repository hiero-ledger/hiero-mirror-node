// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"fmt"
)

const (
	TransactionTypeCryptoCreateAccount int16 = 11
	TransactionTypeCryptoTransfer      int16 = 14
	TransactionTypeTokenCreation       int16 = 29
	TransactionTypeTokenDeletion       int16 = 35
	TransactionTypeTokenUpdate         int16 = 36
	TransactionTypeTokenMint           int16 = 37
	TransactionTypeTokenDissociate     int16 = 41

	transactionTableName = "transaction"
)

type ItemizedTransfer struct {
	Amount     int64    `json:"amount"`
	EntityId   EntityId `json:"entity_id"`
	IsApproval bool     `json:"is_approval"`
}

type ItemizedTransferSlice []ItemizedTransfer

func (i *ItemizedTransferSlice) Scan(value interface{}) error {
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New(fmt.Sprint("Failed to unmarshal JSONB value", value))
	}

	result := ItemizedTransferSlice{}
	err := json.Unmarshal(bytes, &result)
	*i = result
	return err
}

func (i ItemizedTransferSlice) Value() (driver.Value, error) {
	if len(i) == 0 {
		return nil, nil
	}

	return json.Marshal(i)
}

type Transaction struct {
	ConsensusTimestamp       int64 `gorm:"primaryKey"`
	ChargedTxFee             int64
	EntityId                 *EntityId
	Errata                   *string
	InitialBalance           int64
	ItemizedTransfer         ItemizedTransferSlice `gorm:"type:jsonb"`
	MaxFee                   int64
	Memo                     []byte
	NodeAccountId            *EntityId
	Nonce                    int32
	ParentConsensusTimestamp int64
	PayerAccountId           EntityId
	Result                   int16
	Scheduled                bool
	TransactionBytes         []byte
	TransactionHash          []byte
	Type                     int16
	ValidDurationSeconds     int64
	ValidStartNs             int64
}

func (Transaction) TableName() string {
	return transactionTableName
}
