// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract EquivalenceDestruct {
    constructor() payable {}
    // Function to self-destruct the contract
    function destroyContract(address payable beneficiary) public {
        // Self-destruct the contract and send funds to the beneficiary
        selfdestruct(beneficiary);
    }

    fallback() external payable {
    }
}
