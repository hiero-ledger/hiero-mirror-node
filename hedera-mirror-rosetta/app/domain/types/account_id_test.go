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

package types

import (
	"encoding/hex"
	"fmt"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"github.com/thanhpk/randstr"
	"google.golang.org/protobuf/proto"
)

var (
	ecdsaSecp256k1PublicKeyProtoPrefix = []byte{0x3a, 0x21}
	ed25519PublicKeyProtoPrefix        = []byte{0x12, 0x20}

	ecdsaSecp256k1PrivateKey, _  = hiero.PrivateKeyGenerateEcdsa()
	ecdsaSecp256k1PublicKey      = ecdsaSecp256k1PrivateKey.PublicKey()
	ecdsaSecp256k1Alias          = append(ecdsaSecp256k1PublicKeyProtoPrefix, ecdsaSecp256k1PublicKey.BytesRaw()...)
	ecdsaSecp256k1AliasString    = "0x" + hex.EncodeToString(ecdsaSecp256k1Alias)
	ecdsaSecp256k1AliasAccountId = AccountId{
		aliasKey:  &ecdsaSecp256k1PublicKey,
		alias:     ecdsaSecp256k1Alias,
		curveType: types.Secp256k1,
	}

	ed25519PrivateKey, _  = hiero.PrivateKeyGenerateEd25519()
	ed25519PublicKey      = ed25519PrivateKey.PublicKey()
	ed25519Alias          = append(ed25519PublicKeyProtoPrefix, ed25519PublicKey.BytesRaw()...)
	ed25519AliasString    = "0x" + hex.EncodeToString(ed25519Alias)
	ed25519AliasAccountId = AccountId{
		aliasKey:  &ed25519PublicKey,
		alias:     ed25519Alias,
		curveType: types.Edwards25519,
	}

	nonAliasAccountId = AccountId{accountId: domain.MustDecodeEntityId(125)}

	zeroAccountId AccountId
	zeroCurveType types.CurveType
)

func TestAccountIdGetAlias(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected []byte
	}{
		{
			name:     "ZeroAccount",
			input:    AccountId{},
			expected: nil,
		},
		{
			name:     "EcdsaSecp256K1 Alias",
			input:    ecdsaSecp256k1AliasAccountId,
			expected: ecdsaSecp256k1Alias,
		},
		{
			name:     "Ed25519 Alias",
			input:    ed25519AliasAccountId,
			expected: ed25519Alias,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.GetAlias())
		})
	}
}

func TestAccountIdGetCurveType(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected types.CurveType
	}{
		{
			name:     "ZeroAccount",
			input:    zeroAccountId,
			expected: zeroCurveType,
		},
		{
			name:     "EcdsaSecp256k1Alias",
			input:    ecdsaSecp256k1AliasAccountId,
			expected: types.Secp256k1,
		},
		{
			name:     "Ed25519Alias",
			input:    ed25519AliasAccountId,
			expected: types.Edwards25519,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.GetCurveType())
		})
	}
}

func TestAccountIdGetId(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected int64
	}{
		{
			name:     "ZeroAccount",
			input:    zeroAccountId,
			expected: 0,
		},
		{
			name:     "Alias",
			input:    ed25519AliasAccountId,
			expected: 0,
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: 125,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.GetId())
		})
	}
}

func TestAccountIdHasAlias(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected bool
	}{
		{
			name:  "ZeroAccount",
			input: zeroAccountId,
		},
		{
			name:     "Alias",
			input:    ed25519AliasAccountId,
			expected: true,
		},
		{
			name:  "Non-alias",
			input: nonAliasAccountId,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.HasAlias())
		})
	}
}

func TestAccountIdIsZero(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected bool
	}{
		{
			name:     "ZeroAccount",
			input:    AccountId{},
			expected: true,
		},
		{
			name:  "Alias",
			input: ed25519AliasAccountId,
		},
		{
			name:  "Non-alias",
			input: nonAliasAccountId,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.IsZero())
		})
	}
}

func TestAccountIdString(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected string
	}{
		{
			name:     "ZeroAccount",
			input:    zeroAccountId,
			expected: "0.0.0",
		},
		{
			name:     "EcdsaSecp256k1 Alias",
			input:    ecdsaSecp256k1AliasAccountId,
			expected: ecdsaSecp256k1AliasString,
		},
		{
			name:     "Ed25519 Alias",
			input:    ed25519AliasAccountId,
			expected: ed25519AliasString,
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: "0.0.125",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.String())
		})
	}
}

func TestAccountIdToRosetta(t *testing.T) {
	tests := []struct {
		name     string
		input    AccountId
		expected *types.AccountIdentifier
	}{
		{
			name:     "EcdsaSecp256k1 Alias",
			input:    ecdsaSecp256k1AliasAccountId,
			expected: &types.AccountIdentifier{Address: ecdsaSecp256k1AliasString},
		},
		{
			name:     "Ed25519 Alias",
			input:    ed25519AliasAccountId,
			expected: &types.AccountIdentifier{Address: ed25519AliasString},
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: &types.AccountIdentifier{Address: "0.0.125"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.input.ToRosetta())
		})
	}
}

func TestAccountIdToSdkAccountId(t *testing.T) {
	pubKey := ed25519PublicKey
	tests := []struct {
		name     string
		input    AccountId
		expected hiero.AccountID
	}{
		{
			name:     "AliasCopy",
			input:    ed25519AliasAccountId,
			expected: hiero.AccountID{AliasKey: &pubKey},
		},
		{
			name:     "AliasPointer",
			input:    ed25519AliasAccountId,
			expected: hiero.AccountID{AliasKey: &ed25519PublicKey},
		},
		{
			name:     "Non-alias",
			input:    nonAliasAccountId,
			expected: hiero.AccountID{Account: 125},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := tt.input.ToSdkAccountId()
			assert.NoError(t, err)
			assert.Equal(t, tt.expected, actual)
		})
	}
}

func TestAccountIdToSdkAccountIdOutOfRange(t *testing.T) {
	inputs := []AccountId{
		{accountId: domain.EntityId{ShardNum: -1}},
		{accountId: domain.EntityId{RealmNum: -1}},
		{accountId: domain.EntityId{EntityNum: -1}},
	}

	for _, input := range inputs {
		t.Run(input.String(), func(t *testing.T) {
			actual, err := input.ToSdkAccountId()
			assert.Error(t, err)
			assert.Equal(t, hiero.AccountID{}, actual)
		})
	}
}

func TestNewAccountIdFromStringShardRealmAccount(t *testing.T) {
	tests := []struct {
		input     string
		expectErr bool
		expected  string
	}{
		{
			input:    "0.1.2",
			expected: "0.1.2",
		},
		{
			input:     "",
			expectErr: true,
		},
		{
			input:     "a.b.c",
			expectErr: true,
		},
		{
			input:     "0.1.2.3",
			expectErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			accountId, err := NewAccountIdFromString(tt.input, 0, 0)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.expected, accountId.String())
				assert.Nil(t, accountId.GetAlias())
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func TestNewAccountIdFromStringAlias(t *testing.T) {
	tests := []struct {
		input     string
		expectErr bool
		alias     []byte
		curveType types.CurveType
		shard     int64
		realm     int64
	}{
		{
			input:     ecdsaSecp256k1AliasString,
			alias:     ecdsaSecp256k1Alias,
			curveType: types.Secp256k1,
		},
		{
			input:     ed25519AliasString,
			alias:     ed25519Alias,
			curveType: types.Edwards25519,
			realm:     1,
		},
		{
			input:     hex.EncodeToString(ed25519Alias),
			expectErr: true,
		},
		{
			input:     "",
			expectErr: true,
		},
		{
			input:     "0x",
			expectErr: true,
		},
		{
			input:     "0x" + randstr.Hex(10),
			expectErr: true,
		},
		{
			input:     "0xxyz",
			expectErr: true,
		},
		{
			input:     "0x" + ed25519AliasString,
			shard:     -1,
			realm:     -1,
			expectErr: true,
		},
	}

	for _, tt := range tests {
		name := fmt.Sprintf("alias:'%s',shard:%d,reaml:%d", tt.input, tt.shard, tt.realm)
		t.Run(name, func(t *testing.T) {
			accountId, err := NewAccountIdFromString(tt.input, tt.shard, tt.realm)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.alias, accountId.GetAlias())
				assert.Equal(t, tt.curveType, accountId.GetCurveType())
				assert.Equal(t, tt.shard, accountId.accountId.ShardNum)
				assert.Equal(t, tt.realm, accountId.accountId.RealmNum)
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func TestNewAccountIdFromAlias(t *testing.T) {
	tests := []struct {
		input     []byte
		curveType types.CurveType
		shard     int64
		realm     int64
	}{
		{
			input:     ed25519Alias,
			curveType: types.Edwards25519,
		},
		{
			input:     ecdsaSecp256k1Alias,
			curveType: types.Secp256k1,
			realm:     5,
		},
	}

	for _, tt := range tests {
		t.Run(hex.EncodeToString(tt.input), func(t *testing.T) {
			accountId, err := NewAccountIdFromAlias(tt.input, tt.shard, tt.realm)
			assert.Nil(t, err)
			assert.Equal(t, tt.input, accountId.GetAlias())
			assert.Equal(t, tt.curveType, accountId.GetCurveType())
			assert.Equal(t, tt.shard, accountId.accountId.ShardNum)
			assert.Equal(t, tt.realm, accountId.accountId.RealmNum)
		})
	}
}

func TestNewAccountIdFromAliasFail(t *testing.T) {
	tests := []struct {
		alias []byte
		shard int64
		realm int64
	}{
		{alias: []byte{}},
		{alias: ed25519Alias, shard: -1},
		{alias: ed25519Alias, realm: -1},
		{alias: randstr.Bytes(10)},
	}

	for _, tt := range tests {
		t.Run(fmt.Sprintf("%+v", tt), func(t *testing.T) {
			actual, err := NewAccountIdFromAlias(tt.alias, tt.shard, tt.realm)
			assert.Error(t, err)
			assert.Equal(t, zeroAccountId, actual)
		})
	}
}

func TestNewAccountIdFromEntity(t *testing.T) {
	tests := []struct {
		input                 domain.Entity
		expectedAccountString string
		expectedAlias         []byte
		expectedCurveType     types.CurveType
		expectedId            int64
	}{
		{
			input:                 domain.Entity{Id: domain.MustDecodeEntityId(150)},
			expectedAccountString: "0.0.150",
			expectedId:            150,
		},
		{
			input:                 domain.Entity{Id: domain.MustDecodeEntityId(int64(281483566645258))},
			expectedAccountString: "1.2.10",
			expectedId:            281483566645258,
		},
		{
			input:                 domain.Entity{Alias: ecdsaSecp256k1Alias, Id: domain.MustDecodeEntityId(150)},
			expectedAccountString: ecdsaSecp256k1AliasString,
			expectedAlias:         ecdsaSecp256k1Alias,
			expectedCurveType:     types.Secp256k1,
			expectedId:            150,
		},
		{
			input:                 domain.Entity{Alias: ed25519Alias, Id: domain.MustDecodeEntityId(150)},
			expectedAccountString: ed25519AliasString,
			expectedAlias:         ed25519Alias,
			expectedCurveType:     types.Edwards25519,
			expectedId:            150,
		},
	}

	for _, tt := range tests {
		t.Run(tt.expectedAccountString, func(t *testing.T) {
			accountId, err := NewAccountIdFromEntity(tt.input)
			assert.NoError(t, err)
			assert.Equal(t, tt.expectedAccountString, accountId.String())
			assert.Equal(t, tt.expectedAlias, accountId.GetAlias())
			assert.Equal(t, tt.expectedCurveType, accountId.GetCurveType())
			assert.Equal(t, tt.expectedId, accountId.GetId())
		})
	}
}

func TestNewAccountIdFromEntityFail(t *testing.T) {
	tests := []struct {
		name  string
		input domain.Entity
	}{
		{
			name:  "corrupted network alias",
			input: domain.Entity{Alias: randstr.Bytes(16), Id: domain.MustDecodeEntityId(150)},
		},
		{
			name:  "unsupported key type",
			input: domain.Entity{Alias: getKeyListAlias(), Id: domain.MustDecodeEntityId(150)},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			accountId, err := NewAccountIdFromEntity(tt.input)
			assert.Error(t, err)
			assert.Equal(t, AccountId{}, accountId)
		})
	}
}

func TestNewAccountIdFromEntityId(t *testing.T) {
	tests := []struct {
		input    domain.EntityId
		expected string
	}{
		{
			input:    domain.MustDecodeEntityId(150),
			expected: "0.0.150",
		},
		{
			input:    domain.MustDecodeEntityId(int64(281483566645258)),
			expected: "1.2.10",
		},
	}

	for _, tt := range tests {
		t.Run(tt.expected, func(t *testing.T) {
			accountId := NewAccountIdFromEntityId(tt.input)
			assert.Equal(t, tt.expected, accountId.String())
		})
	}
}

func TestNewAccountIdFromPublicKeyBytes(t *testing.T) {
	tests := []struct {
		keyBytes  []byte
		alias     []byte
		curveType types.CurveType
		shard     int64
		realm     int64
	}{
		{
			keyBytes:  ed25519PublicKey.BytesRaw(),
			alias:     ed25519Alias,
			curveType: types.Edwards25519,
		},
		{
			keyBytes:  ecdsaSecp256k1PublicKey.BytesRaw(),
			alias:     ecdsaSecp256k1Alias,
			curveType: types.Secp256k1,
		},
	}

	for _, tt := range tests {
		t.Run(hex.EncodeToString(tt.keyBytes), func(t *testing.T) {
			accountId, err := NewAccountIdFromPublicKeyBytes(tt.keyBytes, tt.shard, tt.realm)
			assert.Nil(t, err)
			assert.Equal(t, tt.alias, accountId.GetAlias())
			assert.Equal(t, tt.curveType, accountId.GetCurveType())
			assert.Equal(t, tt.shard, accountId.accountId.ShardNum)
			assert.Equal(t, tt.realm, accountId.accountId.RealmNum)
		})
	}
}

func TestNewAccountIdFromPublicKeyBytesFail(t *testing.T) {
	tests := []struct {
		keyBytes []byte
		shard    int64
		realm    int64
	}{
		{keyBytes: []byte{}},
		{keyBytes: ed25519PublicKey.BytesRaw(), shard: -1},
		{keyBytes: ed25519PublicKey.BytesRaw(), realm: -1},
		{keyBytes: randstr.Bytes(10)},
	}

	for _, tt := range tests {
		t.Run(fmt.Sprintf("%+v", tt), func(t *testing.T) {
			actual, err := NewAccountIdFromPublicKeyBytes(tt.keyBytes, tt.shard, tt.realm)
			assert.Error(t, err)
			assert.Equal(t, zeroAccountId, actual)
		})
	}
}

func TestNewAccountIdFromSdkAccountId(t *testing.T) {
	tests := []struct {
		input     hiero.AccountID
		expectErr bool
		alias     []byte
		curveType types.CurveType
		hasAlias  bool
	}{
		{input: hiero.AccountID{Account: 150}},
		{input: hiero.AccountID{Shard: 1, Realm: 2, Account: 150}},
		{input: hiero.AccountID{Shard: 1, Realm: 2, Account: 150}},
		{
			input:     hiero.AccountID{Realm: 2, AliasKey: &ed25519PublicKey},
			alias:     ed25519Alias,
			curveType: types.Edwards25519,
			hasAlias:  true,
		},
		{
			input:     hiero.AccountID{Realm: 2, AliasKey: &ecdsaSecp256k1PublicKey},
			alias:     ecdsaSecp256k1Alias,
			curveType: types.Secp256k1,
			hasAlias:  true,
		},
		{input: hiero.AccountID{Shard: 1 << 15}, expectErr: true},
		{input: hiero.AccountID{Realm: 1 << 16}, expectErr: true},
		{input: hiero.AccountID{Account: 1 << 32}, expectErr: true},
		{input: hiero.AccountID{Shard: 9223372036854775808}, expectErr: true},
		{input: hiero.AccountID{Realm: 9223372036854775808}, expectErr: true},
		{input: hiero.AccountID{Account: 9223372036854775808}, expectErr: true},
		{input: hiero.AccountID{AliasKey: &hiero.PublicKey{}}, expectErr: true},
	}

	for _, tt := range tests {
		t.Run(tt.input.String(), func(t *testing.T) {
			accountId, err := NewAccountIdFromSdkAccountId(tt.input)
			if !tt.expectErr {
				assert.Nil(t, err)
				assert.Equal(t, tt.alias, accountId.GetAlias())
				assert.Equal(t, tt.curveType, accountId.GetCurveType())
				assert.Equal(t, tt.hasAlias, accountId.HasAlias())
				sdkAccountId, _ := accountId.ToSdkAccountId()
				assert.Equal(t, tt.input, sdkAccountId)
			} else {
				assert.NotNil(t, err)
				assert.Equal(t, zeroAccountId, accountId)
			}
		})
	}
}

func getKeyListAlias() []byte {
	keyList := services.Key{
		Key: &services.Key_KeyList{
			KeyList: &services.KeyList{
				Keys: []*services.Key{{Key: &services.Key_Ed25519{Ed25519: ed25519PublicKey.BytesRaw()}}}},
		},
	}
	keyListAlias, _ := proto.Marshal(&keyList)
	return keyListAlias
}
