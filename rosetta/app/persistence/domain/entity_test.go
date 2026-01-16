// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"testing"

	"github.com/jackc/pgtype"
	"github.com/stretchr/testify/assert"
)

func TestEntityTableName(t *testing.T) {
	assert.Equal(t, "entity", Entity{}.TableName())
}

func TestEntityGetModifiedTimestamp(t *testing.T) {
	timestamp := int64(100)
	entity := Entity{TimestampRange: pgtype.Int8range{Lower: pgtype.Int8{Int: timestamp, Status: pgtype.Present}}}
	assert.Equal(t, timestamp, entity.GetModifiedTimestamp())
}
func TestEntityHistoryTableName(t *testing.T) {
	assert.Equal(t, "entity_history", Entity{}.HistoryTableName())
}
