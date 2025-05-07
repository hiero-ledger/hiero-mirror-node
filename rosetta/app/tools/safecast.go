// SPDX-License-Identifier: Apache-2.0

package tools

import "errors"

func CastToInt64(value uint64) (int64, error) {
	if value > 9223372036854775807 {
		return 0, errors.New("uint64 out of range")
	}

	return int64(value), nil
}

func CastToUint64(value int64) (uint64, error) {
	if value < 0 {
		return 0, errors.New("int64 out of range")
	}

	return uint64(value), nil
}
