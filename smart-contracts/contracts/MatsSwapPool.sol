// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract MatsSwapPool is Ownable, ReentrancyGuard {
    using SafeERC20 for IERC20;

    uint256 public constant FEE_BPS = 30;
    uint256 public constant BPS_DENOMINATOR = 10_000;

    IERC20 public immutable matsToken;

    event LiquidityAdded(address indexed provider, uint256 tokenAmount, uint256 ethAmount);
    event LiquidityDrained(address indexed receiver, uint256 tokenAmount, uint256 ethAmount);
    event SwapMatsForEth(address indexed trader, uint256 tokenIn, uint256 ethOut);
    event SwapEthForMats(address indexed trader, uint256 ethIn, uint256 tokenOut);

    constructor(address matsTokenAddress, address initialOwner) Ownable(initialOwner) {
        require(matsTokenAddress != address(0), "Mats token is required");
        matsToken = IERC20(matsTokenAddress);
    }

    receive() external payable {}

    function getReserves() public view returns (uint256 tokenReserve, uint256 ethReserve) {
        tokenReserve = matsToken.balanceOf(address(this));
        ethReserve = address(this).balance;
    }

    function quoteMatsToEth(uint256 tokenIn) public view returns (uint256) {
        (uint256 tokenReserve, uint256 ethReserve) = getReserves();
        return _getAmountOut(tokenIn, tokenReserve, ethReserve);
    }

    function quoteEthToMats(uint256 ethIn) public view returns (uint256) {
        (uint256 tokenReserve, uint256 ethReserve) = getReserves();
        return _getAmountOut(ethIn, ethReserve, tokenReserve);
    }

    function addLiquidity(uint256 tokenAmount) external payable onlyOwner {
        require(tokenAmount > 0, "Token amount must be > 0");
        require(msg.value > 0, "ETH amount must be > 0");

        matsToken.safeTransferFrom(msg.sender, address(this), tokenAmount);
        emit LiquidityAdded(msg.sender, tokenAmount, msg.value);
    }

    function drainLiquidity(address payable receiver) external onlyOwner nonReentrant {
        require(receiver != address(0), "Receiver is required");

        uint256 tokenBalance = matsToken.balanceOf(address(this));
        uint256 ethBalance = address(this).balance;

        if (tokenBalance > 0) {
            matsToken.safeTransfer(receiver, tokenBalance);
        }
        if (ethBalance > 0) {
            (bool success, ) = receiver.call{value: ethBalance}("");
            require(success, "ETH transfer failed");
        }

        emit LiquidityDrained(receiver, tokenBalance, ethBalance);
    }

    function swapMatsForEth(uint256 tokenIn, uint256 minEthOut) external nonReentrant returns (uint256 ethOut) {
        require(tokenIn > 0, "Token input must be > 0");

        ethOut = quoteMatsToEth(tokenIn);
        require(ethOut >= minEthOut, "Slippage too high");
        require(ethOut > 0, "ETH output is zero");
        require(address(this).balance >= ethOut, "Insufficient ETH liquidity");

        matsToken.safeTransferFrom(msg.sender, address(this), tokenIn);
        (bool success, ) = payable(msg.sender).call{value: ethOut}("");
        require(success, "ETH transfer failed");

        emit SwapMatsForEth(msg.sender, tokenIn, ethOut);
    }

    function swapEthForMats(uint256 minTokenOut) external payable nonReentrant returns (uint256 tokenOut) {
        require(msg.value > 0, "ETH input must be > 0");

        uint256 tokenReserve = matsToken.balanceOf(address(this));
        uint256 ethReserveBefore = address(this).balance - msg.value;

        tokenOut = _getAmountOut(msg.value, ethReserveBefore, tokenReserve);
        require(tokenOut >= minTokenOut, "Slippage too high");
        require(tokenOut > 0, "Token output is zero");
        require(tokenReserve >= tokenOut, "Insufficient token liquidity");

        matsToken.safeTransfer(msg.sender, tokenOut);
        emit SwapEthForMats(msg.sender, msg.value, tokenOut);
    }

    function _getAmountOut(uint256 amountIn, uint256 reserveIn, uint256 reserveOut) private pure returns (uint256) {
        if (amountIn == 0 || reserveIn == 0 || reserveOut == 0) {
            return 0;
        }

        uint256 amountInWithFee = amountIn * (BPS_DENOMINATOR - FEE_BPS);
        uint256 numerator = amountInWithFee * reserveOut;
        uint256 denominator = (reserveIn * BPS_DENOMINATOR) + amountInWithFee;
        return denominator == 0 ? 0 : numerator / denominator;
    }
}
