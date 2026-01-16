// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"github.com/jackc/pgtype"
	"gorm.io/gorm/clause"
)

type EntityBuilder struct {
	dbClient   interfaces.DbClient
	entity     domain.Entity
	historical bool
}

func (b *EntityBuilder) Alias(alias []byte) *EntityBuilder {
	b.entity.Alias = alias
	return b
}

func (b *EntityBuilder) Deleted(deleted bool) *EntityBuilder {
	b.entity.Deleted = &deleted
	return b
}

func (b *EntityBuilder) Historical(historical bool) *EntityBuilder {
	b.historical = historical
	return b
}

func (b *EntityBuilder) Key(key []byte) *EntityBuilder {
	b.entity.Key = key
	return b
}

func (b *EntityBuilder) ModifiedAfter(delta int64) *EntityBuilder {
	b.entity.TimestampRange = getTimestampRangeWithLower(*b.entity.CreatedTimestamp + delta)
	return b
}

func (b *EntityBuilder) ModifiedTimestamp(timestamp int64) *EntityBuilder {
	b.entity.TimestampRange = getTimestampRangeWithLower(timestamp)
	return b
}

func (b *EntityBuilder) TimestampRange(lowerInclusive, upperExclusive int64) *EntityBuilder {
	b.entity.TimestampRange = pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lowerInclusive, Status: pgtype.Present},
		Upper:     pgtype.Int8{Int: upperExclusive, Status: pgtype.Present},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Exclusive,
		Status:    pgtype.Present,
	}
	return b
}

func (b *EntityBuilder) Persist() domain.Entity {
	tableName := b.entity.TableName()
	if b.historical {
		tableName = b.entity.HistoryTableName()
	}
	tx := b.dbClient.GetDb().Table(tableName)
	if !b.historical {
		// only entity table has unique id column
		tx = tx.Clauses(clause.OnConflict{
			Columns:   []clause.Column{{Name: "id"}},
			DoUpdates: clause.AssignmentColumns([]string{"deleted", "timestamp_range"}),
		})
	}
	tx.Create(&b.entity)
	return b.entity
}

func NewEntityBuilder(dbClient interfaces.DbClient, id, timestamp int64, entityType string) *EntityBuilder {
	entityId := domain.MustDecodeEntityId(id)
	entity := domain.Entity{
		CreatedTimestamp: &timestamp,
		Id:               entityId,
		Num:              entityId.EntityNum,
		Realm:            entityId.RealmNum,
		Shard:            entityId.ShardNum,
		TimestampRange:   getTimestampRangeWithLower(timestamp),
		Type:             entityType,
	}
	return &EntityBuilder{dbClient: dbClient, entity: entity}
}

func getTimestampRangeWithLower(lower int64) pgtype.Int8range {
	return pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lower, Status: pgtype.Present},
		Upper:     pgtype.Int8{Status: pgtype.Null},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Unbounded,
		Status:    pgtype.Present,
	}
}
