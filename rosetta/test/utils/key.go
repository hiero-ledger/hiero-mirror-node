// SPDX-License-Identifier: Apache-2.0

package utils

import (
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/thanhpk/randstr"
	"google.golang.org/protobuf/proto"
)

func EcdsaSecp256k1PublicKey() *services.Key {
	return &services.Key{Key: &services.Key_ECDSASecp256K1{ECDSASecp256K1: randstr.Bytes(33)}}
}

func Ed25519PublicKey() *services.Key {
	return &services.Key{Key: &services.Key_Ed25519{Ed25519: randstr.Bytes(32)}}
}

func KeyListKey(keys []*services.Key) *services.Key {
	return &services.Key{Key: &services.Key_KeyList{KeyList: &services.KeyList{Keys: keys}}}
}

func MustMarshal(m proto.Message) []byte {
	bytes, err := proto.Marshal(m)
	if err != nil {
		panic(err)
	}

	return bytes
}
