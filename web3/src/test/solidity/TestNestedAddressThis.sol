// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.18;

import "./TestAddressThis.sol";

contract TestNestedAddressThis {

    constructor() {
        TestAddressThis child = new TestAddressThis();
        if (address(child) == address(0)) {
            revert("Zero address.");
        }

        if (address(child) == address(this)) {
            revert("Parent and child contract have the same address.");
        }
    }

}