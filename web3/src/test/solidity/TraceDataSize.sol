// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract TraceDataSize {

    function echo(bytes calldata data) external pure returns (bytes memory) {
        return data;
    }

    function callEcho(bytes calldata data, uint256 iterations) external view returns (uint256 total) {
        for (uint256 i = 0; i < iterations; i++) {
            total += this.echo(data).length;
        }
    }
}
