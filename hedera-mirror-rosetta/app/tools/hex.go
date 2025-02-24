// SPDX-License-Identifier: Apache-2.0

package tools

import "strings"

const HexPrefix string = "0x"

// SafeAddHexPrefix - adds 0x prefix to a string if it does not have one
func SafeAddHexPrefix(string string) string {
	if strings.HasPrefix(string, HexPrefix) {
		return string
	}
	return HexPrefix + string
}

// SafeRemoveHexPrefix - removes 0x prefix from a string if it has one
func SafeRemoveHexPrefix(string string) string {
	if strings.HasPrefix(string, HexPrefix) {
		return string[2:]
	}
	return string
}
