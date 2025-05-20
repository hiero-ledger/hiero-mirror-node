// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

contract SelfDestructContract {
    constructor() payable {}
    // Function to self-destruct the contract
    function destructContract(address payable owner) public {
        // Self-destruct the contract and send funds to the beneficiary
        selfdestruct(owner);
    }

    fallback() external payable {
    }
}