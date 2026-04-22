# Ethernest Smart Contracts & Buy Server

This folder contains:
- `contracts/MatsToken.sol` for the ERC-20 token.
- `contracts/MatsSwapPool.sol` for simple MATS/IDRX <-> ETH swap pools on Sepolia.
- `server/buyServer.js` for the Android buy flow through Midtrans.

## Requirements
- Node.js 18+ and npm
- An Infura Project ID
- A Sepolia wallet private key with test ETH for gas
- A Midtrans Sandbox/Production server key for the buy flow

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

## Buy Server
The Android app should never hold a treasury private key. The buy server creates the Midtrans order, returns the Snap redirect URL to the Android WebView, and sends ETH from a treasury wallet only after Midtrans reports a paid transaction.

Required `.env` values:
```
MIDTRANS_SERVER_KEY=your_midtrans_server_key
MIDTRANS_CLIENT_KEY=your_midtrans_client_key_optional
MIDTRANS_IS_PRODUCTION=false
INFURA_PROJECT_ID=your_infura_project_id
BUY_TREASURY_PRIVATE_KEY=private_key_that_funds_testnet_buys_without_0x
BUY_SERVER_PORT=8787
BUY_RPC_URL=
BUY_SPREAD_PERCENT=0
```

Run:
```
npm run start:buy-server
```

Price endpoints use realtime market data in this order:
- CoinGecko ETH/IDR
- Indodax ETH/IDR
- Binance ETHUSDT plus USD/IDR FX rate

Set Android `local.properties`:
```
BUY_BACKEND_BASE_URL=http://10.0.2.2:8787/
```

For a physical device, use the laptop/VPS IP instead of `10.0.2.2`, or run:
```
adb reverse tcp:8787 tcp:8787
```

Midtrans webhook URL:
```
https://your-domain.com/api/midtrans/notification
```

## Notes
- Swap in the Android app is designed for Sepolia testnet only.
- The pool is a simple constant-product pool for learning/demo usage, not a production DEX.
- Keep secrets in `.env`. Never put a treasury private key in the Android app.
