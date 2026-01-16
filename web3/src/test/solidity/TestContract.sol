// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/// @title TestContract
/// @notice A simple contract used for deployment testing
contract TestContract {
    string public name;

    constructor(string memory _name) {
        name = _name;
    }
}
