// SPDX-License-Identifier: Apache-2.0

package domain

import "github.com/jackc/pgtype"

const (
	EntityTypeAccount = "ACCOUNT"

	entityTableName        = "entity"
	entityHistoryTableName = "entity_history"
)

type Entity struct {
	Alias                         []byte
	AutoRenewAccountId            *EntityId
	AutoRenewPeriod               *int64
	CreatedTimestamp              *int64
	Deleted                       *bool
	ExpirationTimestamp           *int64
	Id                            EntityId
	Key                           []byte
	MaxAutomaticTokenAssociations *int32
	Memo                          string
	Num                           int64
	PublicKey                     *string
	ProxyAccountId                *EntityId
	Realm                         int64
	ReceiverSigRequired           *bool
	Shard                         int64
	TimestampRange                pgtype.Int8range
	Type                          string
}

func (e Entity) GetModifiedTimestamp() int64 {
	return e.TimestampRange.Lower.Int
}

func (Entity) TableName() string {
	return entityTableName
}

func (Entity) HistoryTableName() string {
	return entityHistoryTableName
}
