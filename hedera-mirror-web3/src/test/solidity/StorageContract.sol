// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract StorageContract {
    uint256 public slot0;
    uint256 public slot1;
    mapping(uint256 => uint256) public storageMap;
    uint256 public storedValue;

    function updateStorage(uint256 key, uint256 value) public {
        storageMap[key] = value; // SSTORE operation
    }

    function updateSingleValue(uint256 value) public {
        storedValue = value; // SSTORE operation
    }

    function setSlot0(uint256 value) public {
        slot0 = value;
    }

    function setSlot1(uint256 value) public {
        slot1 = value;
    }
}