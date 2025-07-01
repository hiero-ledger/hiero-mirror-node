// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.18;

contract TestAddressThis {

    constructor() payable {
        address test = address(this);
        if (test == address(0)) {
            revert("Zero address.");
        }
    }

    function testAddressThisFunction() public view {
        address test = address(this);
        if (test == address(0)) {
            revert("Zero address.");
        }
    }

    function getAddressThis() public view returns (address) {
        return address(this);
    }

    function getBalance(address _to) external view returns (uint256) {
        return _to.balance;
    }

    function getAddressThisBalance() external returns (uint256)  {
        return address(this).balance;
    }
}