// SPDX-License-Identifier: Apache-2.0

package tools

func GetStringValuesFromInt32StringMap(mapping map[int32]string) []string {
	var values []string

	for _, v := range mapping {
		values = append(values, v)
	}

	return values
}
