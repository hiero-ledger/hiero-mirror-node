// SPDX-License-Identifier: Apache-2.0

package domain

import "github.com/hiero-ledger/hiero-sdk-go/v2/sdk"

func GenEd25519KeyPair() (hiero.PrivateKey, hiero.PublicKey) {
	sk, err := hiero.PrivateKeyGenerateEd25519()
	if err != nil {
		panic(err)
	}
	return sk, sk.PublicKey()
}
