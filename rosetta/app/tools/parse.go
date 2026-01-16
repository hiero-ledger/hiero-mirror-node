// SPDX-License-Identifier: Apache-2.0

package tools

import "strconv"

func ToInt64(value string) (int64, error) {
	return strconv.ParseInt(value, 10, 64)
}

func SafeUnquote(s string) string {
	if r, err := strconv.Unquote(s); err == nil {
		return r
	}

	return s
}
