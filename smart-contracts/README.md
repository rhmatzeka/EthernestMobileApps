# Mats Wallet Smart Contracts (Sepolia)

This folder contains:
- `MatsToken.sol` for the ERC-20 token
- `MatsSwapPool.sol` for a simple on-chain MATS <-> ETH swap pool on Sepolia

## Requirements
- Node.js 18+ and npm
- An Infura Project ID
- A Sepolia wallet private key with test ETH for gas

## Setup
1. Install deps:
   - `npm install`
2. Create `.env` based on `.env.example`:
   - `INFURA_PROJECT_ID=...`
   - `DEPLOYER_PRIVATE_KEY=...` (no `0x` prefix)

## Compile
`npm run compile`

## Deploy Token
`npm run deploy:token`

After deploy, copy the printed token contract address to `.env` as:
`MATS_TOKEN_ADDRESS=...`

## Deploy Swap Pool
`npm run deploy:pool`

After deploy, copy the printed pool contract address to `.env` as:
`MATS_SWAP_POOL_ADDRESS=...`

## Seed Pool Liquidity
This step sends test ETH + MATS into the pool so swap becomes usable.

`npm run seed:pool`

## Notes
- Swap in the Android app is designed for Sepolia testnet only.
- The pool is a simple constant-product pool for learning/demo usage, not a production DEX.
