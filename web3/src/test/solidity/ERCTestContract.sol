// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Enumerable.sol";

contract ERCTestContract {

    //Read operations

    function name(address token) public view returns (string memory) {
        return IERC20Metadata(token).name();
    }

    function symbol(address token) public view returns (string memory) {
        return IERC20Metadata(token).symbol();
    }

    function decimals(address token) public view returns (uint8) {
        return IERC20Metadata(token).decimals();
    }

    function totalSupply(address token) external view returns (uint256) {
        return IERC20(token).totalSupply();
    }

    function balanceOf(address token, address account) external view returns (uint256) {
        return IERC20(token).balanceOf(account);
    }

    function allowance(address token, address owner, address spender) external view returns (uint256) {
        return IERC20(token).allowance(owner, spender);
    }

    function getApproved(address token, uint256 tokenId) external view returns (address) {
        return IERC721(token).getApproved(tokenId);
    }

    function isApprovedForAll(address token, address owner, address operator) public view returns (bool) {
        return IERC721(token).isApprovedForAll(owner, operator);
    }

    function setApprovalForAll(address token, address operator, bool approved) public {
        return IERC721(token).setApprovalForAll(operator, approved);
    }

    function getOwnerOf(address token, uint256 serialNo) external view returns(address){
        return IERC721(token).ownerOf(serialNo);
    }

    function tokenURI(address token, uint256 tokenId) public view returns (string memory) {
        return IERC721Metadata(token).tokenURI(tokenId);
    }

    //Read operations NonStatic

    function nameNonStatic(address token) public returns (string memory) {
        return IERC20Metadata(token).name();
    }

    function symbolNonStatic(address token) public returns (string memory) {
        return IERC20Metadata(token).symbol();
    }

    function decimalsNonStatic(address token) public returns (uint8) {
        return IERC20Metadata(token).decimals();
    }

    function totalSupplyNonStatic(address token) external returns (uint256) {
        return IERC20(token).totalSupply();
    }

    function balanceOfNonStatic(address token, address account) external returns (uint256) {
        return IERC20(token).balanceOf(account);
    }

    function allowanceNonStatic(address token, address owner, address spender) external returns (uint256) {
        return IERC20(token).allowance(owner, spender);
    }

    function getApprovedNonStatic(address token, uint256 tokenId) external returns (address) {
        return IERC721(token).getApproved(tokenId);
    }

    function isApprovedForAllNonStatic(address token, address owner, address operator) public returns (bool) {
        return IERC721(token).isApprovedForAll(owner, operator);
    }

    function getOwnerOfNonStatic(address token, uint256 serialNo) external returns(address){
        return IERC721(token).ownerOf(serialNo);
    }

    function tokenURINonStatic(address token, uint256 tokenId) public returns (string memory) {
        return IERC721Metadata(token).tokenURI(tokenId);
    }


    //Modification operations

    function transfer(address token, address recipient, uint256 amount) public {
        IERC20(token).transfer(recipient, amount);
    }

    function delegateTransfer(address token, address recipient, uint256 amount) public {
        (bool success, bytes memory result) = address(IERC20(token)).delegatecall(abi.encodeWithSignature("transfer(address,uint256)", recipient, amount));
    }

    function transferFrom(address token, address sender, address recipient, uint256 amount) public {
        IERC20(token).transferFrom(sender, recipient, amount);
    }

    function transferFromThenRevert(address token, address sender, address recipient, uint256 amount) public {
        IERC20(token).transferFrom(sender, recipient, amount);
        revert();
    }

    function approve(address token, address spender, uint256 amount) public {
        IERC20(token).approve(spender, amount);
    }

    function approveNFT(address token, address spender, uint256 tokenId) public {
        IERC721(token).approve(spender, tokenId);
    }

    function transferFromNFT(address token, address from, address to, uint256 tokenId) public {
        IERC721(token).transferFrom(from, to, tokenId);
    }

    fallback() external payable {}
}
