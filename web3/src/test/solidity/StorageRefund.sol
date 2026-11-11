// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract StorageRefund {
    uint256[100] private values;

    function clearAll() external returns (uint256 total) {
        for (uint256 start = 0; start < 100; start += 50) {
            (bool success, bytes memory result) = address(this).delegatecall(
                abi.encodeCall(this.clearRange, (start, start + 50))
            );
            require(success, "Storage clear failed");
            total += abi.decode(result, (uint256));
        }
    }

    function clearRange(uint256 start, uint256 end) external returns (uint256 total) {
        for (uint256 i = start; i < end; ++i) {
            total += values[i];
            delete values[i];
        }
    }
}
