// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract JumboTransaction {

    function consumeLargeCalldata(bytes calldata input) external pure returns (uint256) {
        require(input.length > 6144, "Input too small");

        uint256 sum;
        for (uint256 i = 0; i < input.length; i++) {
            sum += uint8(input[i]);
        }

        return sum;
    }
}
