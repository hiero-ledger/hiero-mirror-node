// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Enumerable.sol";

contract ERCTestContract {
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

    function approve(address token, address spender, uint256 amount) external returns (bool){
        return IERC20(token).approve(spender, amount);
    }

    function transfer(address token, address to, uint256 amount) external returns (bool){
        return IERC20(token).transfer(to, amount);
    }

    function transferFrom(address token, address from, address to, uint256 amount) external returns (bool){
        return IERC20(token).transferFrom(from, to, amount);
    }

    function getApproved(address token, uint256 tokenId) external view returns (address) {
        return IERC721(token).getApproved(tokenId);
    }

    function isApprovedForAll(address token, address owner, address operator) public view returns (bool) {
        return IERC721(token).isApprovedForAll(owner, operator);
    }

    function getOwnerOf(address token, uint256 serialNo) external view returns (address){
        return IERC721(token).ownerOf(serialNo);
    }

    function tokenURI(address token, uint256 tokenId) public view returns (string memory) {
        return IERC721Metadata(token).tokenURI(tokenId);
    }

    function nameIERC721(address token) public view returns (string memory) {
        return IERC721Metadata(token).name();
    }

    function symbolIERC721(address token) public view returns (string memory) {
        return IERC721Metadata(token).symbol();
    }

    function totalSupplyIERC721(address token) external view returns (uint256) {
        return IERC721Enumerable(token).totalSupply();
    }

    function balanceOfIERC721(address token, address account) external view returns (uint256) {
        return IERC721(token).balanceOf(account);
    }
}
