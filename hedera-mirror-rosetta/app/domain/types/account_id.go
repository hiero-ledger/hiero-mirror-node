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
	"strings"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
)

type AccountId struct {
	accountId domain.EntityId
	alias     []byte
	aliasKey  *hiero.PublicKey
	curveType types.CurveType
}

// GetAlias returns the Hedera network alias
func (a AccountId) GetAlias() []byte {
	if !a.HasAlias() {
		return nil
	}

	alias := make([]byte, len(a.alias))
	copy(alias, a.alias)
	return alias
}

func (a AccountId) GetCurveType() types.CurveType {
	return a.curveType
}

func (a AccountId) GetId() int64 {
	return a.accountId.EncodedId
}

func (a AccountId) HasAlias() bool {
	return len(a.alias) != 0
}

func (a AccountId) IsZero() bool {
	return !a.HasAlias() && a.accountId.EncodedId == 0
}

func (a AccountId) String() string {
	if a.HasAlias() {
		return tools.SafeAddHexPrefix(hex.EncodeToString(a.alias))
	}
	return a.accountId.String()
}

func (a AccountId) ToRosetta() *types.AccountIdentifier {
	return &types.AccountIdentifier{Address: a.String()}
}

func (a AccountId) ToSdkAccountId() (zero hiero.AccountID, err error) {
	shard, err := tools.CastToUint64(a.accountId.ShardNum)
	if err != nil {
		return zero, err
	}

	realm, err := tools.CastToUint64(a.accountId.RealmNum)
	if err != nil {
		return zero, err
	}

	account, err := tools.CastToUint64(a.accountId.EntityNum)
	if err != nil {
		return zero, err
	}

	return hiero.AccountID{
		Shard:    shard,
		Realm:    realm,
		Account:  account,
		AliasKey: a.aliasKey,
	}, nil
}

func NewAccountIdFromAlias(alias []byte, shard, realm int64) (zero AccountId, _ error) {
	if shard < 0 || realm < 0 {
		return zero, errors.Errorf("shard and realm must be positive integers")
	}

	curveType, publicKey, err := NewPublicKeyFromAlias(alias)
	if err != nil {
		return zero, err
	}

	return AccountId{
		accountId: domain.EntityId{ShardNum: shard, RealmNum: realm},
		alias:     alias,
		aliasKey:  &publicKey.PublicKey,
		curveType: curveType,
	}, nil
}

// NewAccountIdFromEntity creates AccountId from the entity. If the entity has a network alias, the function will parse
// it to the rosetta format
func NewAccountIdFromEntity(entity domain.Entity) (zero AccountId, _ error) {
	if len(entity.Alias) == 0 {
		return NewAccountIdFromEntityId(entity.Id), nil
	}

	curveType, publicKey, err := NewPublicKeyFromAlias(entity.Alias)
	if err != nil {
		return zero, err
	}

	return AccountId{
		accountId: entity.Id,
		alias:     entity.Alias,
		aliasKey:  &publicKey.PublicKey,
		curveType: curveType,
	}, nil
}

func NewAccountIdFromEntityId(accountId domain.EntityId) AccountId {
	return AccountId{accountId: accountId}
}

func NewAccountIdFromPublicKeyBytes(keyBytes []byte, shard, realm int64) (zero AccountId, _ error) {
	if shard < 0 || realm < 0 {
		return zero, errors.Errorf("shard and realm must be positive integers")
	}

	aliasKey, err := hiero.PublicKeyFromBytes(keyBytes)
	if err != nil {
		return zero, err
	}

	alias, curveType, err := PublicKey{aliasKey}.ToAlias()
	if err != nil {
		return zero, err
	}

	return AccountId{
		accountId: domain.EntityId{ShardNum: shard, RealmNum: realm},
		alias:     alias,
		aliasKey:  &aliasKey,
		curveType: curveType,
	}, nil
}

func NewAccountIdFromSdkAccountId(accountId hiero.AccountID) (zero AccountId, _ error) {
	var alias []byte
	var entityId domain.EntityId
	var err error
	var curveType types.CurveType

	shard, realm, account, err := castFromSdkAccountId(accountId)
	if err != nil {
		return zero, err
	}

	if accountId.AliasKey != nil {
		entityId = domain.EntityId{ShardNum: shard, RealmNum: realm}
		alias, curveType, err = PublicKey{*accountId.AliasKey}.ToAlias()
	} else {
		entityId, err = domain.EntityIdOf(shard, realm, account)
	}

	if err != nil {
		return zero, err
	}

	return AccountId{
		accountId: entityId,
		alias:     alias,
		aliasKey:  accountId.AliasKey,
		curveType: curveType,
	}, nil
}

// NewAccountIdFromString creates AccountId from the address string. If the address is in the shard.realm.num form,
// shard and realm are ignored. The only valid form of the alias address is the hex string of the raw public key bytes.
func NewAccountIdFromString(address string, shard, realm int64) (zero AccountId, _ error) {
	if strings.Contains(address, ".") {
		entityId, err := domain.EntityIdFromString(address)
		if err != nil {
			return zero, err
		}
		return AccountId{accountId: entityId}, nil
	}

	if !strings.HasPrefix(address, tools.HexPrefix) {
		return zero, errors.Errorf("Invalid Account Alias")
	}

	alias, err := hex.DecodeString(tools.SafeRemoveHexPrefix(address))
	if err != nil {
		return zero, err
	}

	return NewAccountIdFromAlias(alias, shard, realm)
}

func castFromSdkAccountId(accountId hiero.AccountID) (shard, realm, account int64, err error) {
	shard, err = tools.CastToInt64(accountId.Shard)
	if err != nil {
		return
	}

	realm, err = tools.CastToInt64(accountId.Realm)
	if err != nil {
		return
	}

	if accountId.AliasKey == nil {
		account, err = tools.CastToInt64(accountId.Account)
		if err != nil {
			return
		}
	}

	return
}
