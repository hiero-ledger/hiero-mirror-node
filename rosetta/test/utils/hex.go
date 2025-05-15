// SPDX-License-Identifier: Apache-2.0

package utils

import (
	"encoding/hex"
	"strings"
)

func MustDecode(input string) []byte {
	input = strings.TrimPrefix(input, "0x")
	decoded, err := hex.DecodeString(input)
	if err != nil {
		panic(err)
	}
	return decoded
}
