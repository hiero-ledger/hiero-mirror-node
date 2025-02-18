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
	"fmt"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
	"google.golang.org/protobuf/proto"
)

const (
	ecdsaSecp256k1PublicKeySize = 33
	ed25519PublicKeySize        = 32
)

// PublicKey embed hiero.PublicKey and implement the Unmarshaler interface
type PublicKey struct {
	hiero.PublicKey
}

func (pk PublicKey) IsEmpty() bool {
	return len(pk.PublicKey.Bytes()) == 0
}

func (pk PublicKey) ToAlias() (_ []byte, zeroCurveType types.CurveType, _ error) {
	rawKey := pk.PublicKey.BytesRaw()
	var key services.Key
	var curveType types.CurveType
	switch keySize := len(rawKey); keySize {
	case ed25519PublicKeySize:
		curveType = types.Edwards25519
		key = services.Key{Key: &services.Key_Ed25519{Ed25519: rawKey}}
	case ecdsaSecp256k1PublicKeySize:
		curveType = types.Secp256k1
		key = services.Key{Key: &services.Key_ECDSASecp256K1{ECDSASecp256K1: rawKey}}
	default:
		return nil, zeroCurveType, errors.New(fmt.Sprintf("Unknown public key type with %d raw bytes", keySize))
	}

	// HIP-32 defines the alias as "a byte array (protobuf bytes) that is formed by serializing a protobuf Key
	// that represents a primitive public key (not a threshold key or key list, etc.)".
	alias, err := proto.Marshal(&key)
	if err != nil {
		return nil, zeroCurveType, errors.New(fmt.Sprintf("Failed to marshal proto Key: %s", err))
	}
	return alias, curveType, nil
}

func (pk *PublicKey) UnmarshalJSON(data []byte) error {
	var err error
	pk.PublicKey, err = hiero.PublicKeyFromString(tools.SafeUnquote(string(data)))
	return err
}

func NewPublicKeyFromAlias(alias []byte) (zeroCurveType types.CurveType, zeroPublicKey PublicKey, _ error) {
	if len(alias) == 0 {
		return zeroCurveType, zeroPublicKey, errors.Errorf("Empty alias provided")
	}

	var key services.Key
	if err := proto.Unmarshal(alias, &key); err != nil {
		return zeroCurveType, zeroPublicKey, err
	}

	var curveType types.CurveType
	var rawKey []byte
	switch value := key.GetKey().(type) {
	case *services.Key_Ed25519:
		curveType = types.Edwards25519
		rawKey = value.Ed25519
	case *services.Key_ECDSASecp256K1:
		curveType = types.Secp256k1
		rawKey = value.ECDSASecp256K1
	default:
		return zeroCurveType, zeroPublicKey, errors.Errorf("Unsupported key type")
	}

	publicKey, err := hiero.PublicKeyFromBytes(rawKey)
	if err != nil {
		return zeroCurveType, zeroPublicKey, err
	}

	return curveType, PublicKey{publicKey}, nil
}
