// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"encoding/json"
	"fmt"
	"math"
	"testing"

	"github.com/stretchr/testify/assert"
)

var invalidEntityIdParts = [][3]int64{
	{-1, 0, 0},
	{0, -1, 0},
	{0, 0, -1},
	{2 << 10, 0, 0},
	{0, 2 << 16, 0},
	{0, 0, 2 << 38},
}

var invalidEntityIdStrs = []string{
	"abc",
	"a.0.0",
	"0.b.0",
	"0.0.c",
	"-1.0.0",
	"0.-1.0",
	"0.0.-1",
}

func TestEntityIdIsZero(t *testing.T) {
	entityId := &EntityId{}
	assert.True(t, entityId.IsZero())
}

func TestEntityIdIsNotZero(t *testing.T) {
	entityId, _ := DecodeEntityId(12)
	assert.False(t, entityId.IsZero())
}

func TestEntityIdScan(t *testing.T) {
	var tests = []struct {
		name     string
		value    interface{}
		expected *EntityId
	}{
		{
			name:     "Success",
			value:    int64(100),
			expected: &EntityId{EntityNum: 100, EncodedId: 100},
		},
		{
			name:     "Max",
			value:    int64(math.MaxInt64),
			expected: &EntityId{ShardNum: 511, RealmNum: 65535, EntityNum: 274877906943, EncodedId: math.MaxInt64},
		},
		{
			name:     "Min",
			value:    int64(math.MinInt64),
			expected: &EntityId{ShardNum: 512, RealmNum: 0, EntityNum: 0, EncodedId: math.MinInt64},
		},
		{
			name:     "InvalidType",
			value:    "100",
			expected: nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual := &EntityId{}
			err := actual.Scan(tt.value)
			if tt.expected != nil {
				assert.Equal(t, tt.expected, actual)
			} else {
				assert.Error(t, err)
			}
		})
	}
}

func TestEntityIdString(t *testing.T) {
	entityId := EntityId{EntityNum: 7, EncodedId: 7}

	assert.Equal(t, "0.0.7", entityId.String())
}

type E struct {
	EntityId EntityId `json:"entity_id"`
}

func TestEntityIdMarshalJSON(t *testing.T) {
	var tests = []struct {
		input    E
		expected []byte
	}{
		{
			input:    E{},
			expected: []byte("{\"entity_id\":0}"),
		},
		{
			input:    E{EntityId{EntityNum: 10, EncodedId: 10}},
			expected: []byte("{\"entity_id\":10}"),
		},
	}

	for _, tt := range tests {
		t.Run(tt.input.EntityId.String(), func(t *testing.T) {
			actual, err := json.Marshal(tt.input)
			assert.NoError(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestEntityIdUnmarshalJSON(t *testing.T) {
	var tests = []struct {
		entity   string
		expected EntityId
	}{
		{
			entity:   "0",
			expected: EntityId{},
		},
		{
			entity:   "\"0.0.0\"",
			expected: EntityId{},
		},
		{
			entity: "10",
			expected: EntityId{
				EntityNum: 10,
				EncodedId: 10,
			},
		},
		{
			entity: "\"0.0.10\"",
			expected: EntityId{
				EntityNum: 10,
				EncodedId: 10,
			},
		},
		{
			entity: "4294967295",
			expected: EntityId{
				EntityNum: 4294967295,
				EncodedId: 4294967295,
			},
		},
		{
			entity: "\"0.0.4294967295\"",
			expected: EntityId{
				EntityNum: 4294967295,
				EncodedId: 4294967295,
			},
		},
		{
			entity: "18014948265295882",
			expected: EntityId{
				ShardNum:  1,
				RealmNum:  2,
				EntityNum: 10,
				EncodedId: 18014948265295882,
			},
		},
		{
			entity: "\"1.2.10\"",
			expected: EntityId{
				ShardNum:  1,
				RealmNum:  2,
				EntityNum: 10,
				EncodedId: 18014948265295882,
			},
		},
		{
			entity:   "null",
			expected: EntityId{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.entity, func(t *testing.T) {
			input := fmt.Sprintf("{\"entity_id\": %s}", tt.entity)

			e := &E{}
			err := json.Unmarshal([]byte(input), e)

			assert.NoError(t, err)
			assert.Equal(t, tt.expected, e.EntityId)
		})
	}
}

func TestEntityIdUnmarshalJSONThrows(t *testing.T) {
	strs := make([]string, 0)
	for _, invalid := range invalidEntityIdStrs {
		strs = append(strs, fmt.Sprintf("\"%s\"", invalid))
	}

	strs = append(strs, "-9223372036854775809")

	for _, str := range strs {
		t.Run(str, func(t *testing.T) {
			input := fmt.Sprintf("{\"entity_id\": %s}", str)

			e := &E{}
			err := json.Unmarshal([]byte(input), e)

			assert.Error(t, err)
		})
	}
}

func TestEntityIdValueZero(t *testing.T) {
	entityId := EntityId{}
	actual, err := entityId.Value()
	assert.NoError(t, err)
	assert.Equal(t, nil, actual)
}

func TestEntityIdValueNonZero(t *testing.T) {
	entityId := MustDecodeEntityId(600)
	actual, err := entityId.Value()
	assert.NoError(t, err)
	assert.Equal(t, int64(600), actual)
}

func TestEntityIdEncoding(t *testing.T) {
	var testData = []struct {
		shard, realm, number, expected int64
	}{
		{0, 0, 0, 0},
		{0, 0, 10, 10},
		{0, 0, 274877906943, 274877906943},
		{1023, 65535, 274877906943, -1},
		{511, 65535, 274877906943, 9223372036854775807},
		{512, 0, 0, -9223372036854775808},
	}

	for _, tt := range testData {
		res, err := EncodeEntityId(tt.shard, tt.realm, tt.number)

		assert.NoError(t, err)
		assert.Equal(t, tt.expected, res)
	}
}

func TestEntityIdEncodeThrows(t *testing.T) {
	var testData = []struct {
		shard, realm, number int64
	}{
		{1024, 0, 0},         // 1 << shardBits
		{0, 65536, 0},        // 1 << realmBits
		{0, 0, 274877906944}, // 1 << numberBits
		{-1, 0, 0},
		{0, -1, 0},
		{0, 0, -1},
	}

	for _, tt := range testData {
		res, err := EncodeEntityId(tt.shard, tt.realm, tt.number)
		assert.Error(t, err)
		assert.Equal(t, int64(0), res)
	}
}

func TestEntityIdFromString(t *testing.T) {
	var testData = []struct {
		expected EntityId
		entity   string
	}{
		{EntityId{}, "0.0.0"},
		{EntityId{EntityNum: 10, EncodedId: 10}, "0.0.10"},
		{EntityId{EntityNum: 4294967295, EncodedId: 4294967295}, "0.0.4294967295"},
	}

	for _, tt := range testData {
		res, err := EntityIdFromString(tt.entity)
		assert.Nil(t, err)
		assert.Equal(t, tt.expected, res)
	}
}

func TestEntityIdFromStringThrows(t *testing.T) {
	for _, tt := range invalidEntityIdStrs {
		res, err := EntityIdFromString(tt)
		assert.Equal(t, EntityId{}, res)
		assert.Error(t, err)
	}
}

func TestEntityIdOf(t *testing.T) {
	var testData = []struct {
		expected EntityId
		parts    [3]int64
	}{
		{EntityId{}, [3]int64{0, 0, 0}},
		{EntityId{EntityNum: 10, EncodedId: 10}, [3]int64{0, 0, 10}},
		{EntityId{EntityNum: 4294967295, EncodedId: 4294967295}, [3]int64{0, 0, 4294967295}},
	}

	for _, tt := range testData {
		res, err := EntityIdOf(tt.parts[0], tt.parts[1], tt.parts[2])
		assert.Nil(t, err)
		assert.Equal(t, tt.expected, res)
	}
}

func TestEntityIdOfThrows(t *testing.T) {
	for _, tt := range invalidEntityIdParts {
		res, err := EntityIdOf(tt[0], tt[1], tt[2])
		assert.Equal(t, EntityId{}, res)
		assert.Error(t, err)
	}
}

func TestEntityIdDecoding(t *testing.T) {
	var testData = []struct {
		input    int64
		expected EntityId
	}{
		{0, EntityId{}},
		{10, EntityId{EntityNum: 10, EncodedId: 10}},
		{274877906943, EntityId{EntityNum: 274877906943, EncodedId: 274877906943}},
		{180146733873889290, EntityId{
			ShardNum:  10,
			RealmNum:  10,
			EntityNum: 10,
			EncodedId: 180146733873889290,
		}},
		{9223372036854775807, EntityId{
			ShardNum:  511,
			RealmNum:  65535,
			EntityNum: 274877906943,
			EncodedId: 9223372036854775807,
		}},
		{9205357638345293824, EntityId{
			ShardNum:  511,
			EncodedId: 9205357638345293824,
		}},
		{-9223372036854775808, EntityId{
			ShardNum:  512,
			RealmNum:  0,
			EntityNum: 0,
			EncodedId: -9223372036854775808,
		}},
	}

	for _, tt := range testData {
		res, err := DecodeEntityId(tt.input)

		assert.Nil(t, err)
		assert.Equal(t, tt.expected, res)
	}
}
