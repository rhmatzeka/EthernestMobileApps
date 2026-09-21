# Ethernest

A multi-chain crypto wallet app for Android. You can hold ETH and tokens on many EVM networks, send and receive with QR codes, swap tokens on testnet, view your NFTs, and **buy ETH with Indonesian payment methods** through Midtrans.

## Features

- **Create or import a wallet**, protected by a PIN (ready for fingerprint login)
- **Many networks**: Ethereum, Sepolia, BSC, Avalanche, Polygon, Arbitrum, Optimism, Base, Fantom, or any EVM network through a **custom RPC**
- **Your assets**: ETH, ERC-20 tokens, and NFTs (ERC-721 and ERC-1155), with real coin icons, candlestick price charts, and transaction history
- **Send and receive** ETH with QR codes, a set amount, address sharing, and "deposit from exchange"
- **Swap tokens** on-chain: MATS, IDRX, popular EVM tokens, and any extra token that has a pool
- **Buy ETH** inside the app: pay through Midtrans in an in-app page, with a live ETH/IDR price

## Tech stack

- **App**: Java 11, Android SDK 36, Material Design 3, RecyclerView, ViewPager2, WebView
- **Blockchain**: Web3j 4.9 with Infura RPC
- **Data**: Retrofit + Gson (CoinGecko, Etherscan), Room
- **Security**: EncryptedSharedPreferences and the Android Keystore
- **Contracts and buy server**: Solidity, Hardhat, Express.js, Midtrans

## How the project is split

| Folder | What it is |
| --- | --- |
| `app/` | The Android app |
| `smart-contracts/contracts/` | ERC-20 token and swap pool contracts |
| `smart-contracts/scripts/` | Deploy, mint, and liquidity scripts |
| `smart-contracts/server/` | The "buy ETH" backend (Express + Midtrans) |

## Set up the Android app

1. Open the project in Android Studio.
2. Create `local.properties` in the project root (see `local.properties.example`) and fill it in:

   ```properties
   INFURA_PROJECT_ID=xxx
   ETHERSCAN_API_KEY=xxx
   BSC_RPC_URL=https://bsc-dataseed.bnbchain.org
   AVALANCHE_RPC_URL=https://api.avax.network/ext/bc/C/rpc
   POLYGON_RPC_URL=https://polygon.drpc.org

   # Swap tokens and pools (from the contract deploy step below)
   MATS_TOKEN_ADDRESS=xxx
   MATS_SWAP_POOL_ADDRESS=xxx
   IDRX_TOKEN_ADDRESS=xxx
   IDRX_SWAP_POOL_ADDRESS=xxx
   # Optional presets: SWAP_USDT_*, SWAP_USDC_*, ...
   # Optional extra slots: SWAP_TOKEN_1_* to SWAP_TOKEN_12_*

   # Buy ETH backend
   BUY_BACKEND_BASE_URL=http://10.0.2.2:8787/
   MIDTRANS_PAYMENT_URL=
   ```

3. Sync Gradle and run the app.

**Reaching the buy server from the app:**

- The Android **emulator** reaches your computer at `http://10.0.2.2:8787/`.
- A **real phone** needs your computer's or server's IP, for example `http://192.168.1.10:8787/`.
- Over **USB**, you can also run `adb reverse tcp:8787 tcp:8787`.

## Buy server (Midtrans)

The app **never stores the treasury's private key**. Buying ETH works like this:

1. The app creates an order on the backend.
2. The backend creates a Midtrans payment, which opens inside the app.
3. Once Midtrans confirms the payment, the backend sends ETH to your wallet.

The ETH/IDR price is calculated on the server in real time from CoinGecko, then Indodax, with Binance ETH/USDT plus the USD/IDR rate as a fallback.

```bash
cd smart-contracts
npm install
cp .env.example .env
npm run start:buy-server
```

Minimum `.env` for the buy server:

```env
MIDTRANS_SERVER_KEY=xxx
MIDTRANS_IS_PRODUCTION=false
INFURA_PROJECT_ID=xxx
BUY_TREASURY_PRIVATE_KEY=testnet_treasury_key_without_0x
BUY_DEV_MODE=true
```

| Endpoint | What it does |
| --- | --- |
| `GET /health` | Health check (try http://127.0.0.1:8787/health) |
| `GET /api/price/eth-idr` | Current ETH price in rupiah |
| `POST /api/buy/eth` | Create a buy order |
| `POST /api/midtrans/notification` | Midtrans payment webhook |

For production, run the buy server on a server with HTTPS and set the Midtrans webhook to `https://your-domain.com/api/midtrans/notification`.

## Smart contracts and swaps

The `smart-contracts` folder has an ERC-20 token and simple ETH/token swap pools for testnet.

How swaps work in the app:

- A token can be swapped once it has a **token address, a pool address, and liquidity**. By default that's `MATS` and `IDRX`.
- Popular tokens (`USDT`, `USDC`, `DAI`, `WBTC`, `LINK`, `UNI`, `AAVE`, `SHIB`, `PEPE`, `ARB`, `OP`) also appear in the picker.
- A token without a pool shows **"Butuh pool"** (needs a pool) and its swap button stays locked. Once the pool is set up it shows **"Aktif"** (active).

### Fastest way: deploy everything on Sepolia

Get some Sepolia ETH from a faucet first, then:

```bash
cd smart-contracts
cp .env.example .env
```

```env
INFURA_PROJECT_ID=xxx
DEPLOYER_PRIVATE_KEY=testnet_key_without_0x
TEST_SWAP_TOKENS=MATS,IDRX,USDT,USDC,DAI,WBTC,LINK,UNI,AAVE,SHIB,PEPE,ARB,OP
TEST_POOL_ETH_LIQUIDITY=0.005
```

```bash
npm install
npm run compile
npm run deploy:testnet-routes
```

The script deploys mock tokens, creates an ETH/token pool for each, adds liquidity, and **prints the lines to paste into `local.properties`**. Paste them, sync Gradle, and run the app.

### Step by step: one token and one pool

```env
INFURA_PROJECT_ID=xxx
DEPLOYER_PRIVATE_KEY=xxx_without_0x
TOKEN_NAME=Mats Token
TOKEN_SYMBOL=MATS
TOKEN_INITIAL_SUPPLY=1000000
POOL_TOKEN_SYMBOL=MATS
POOL_TOKEN_ADDRESS=your_token_address
POOL_SWAP_ADDRESS=your_pool_address
```

```bash
npm run compile
npm run deploy:token   # deploy the ERC-20 token
npm run deploy:pool    # deploy its swap pool
npm run seed:pool      # add liquidity
```

For another token, set `POOL_TOKEN_SYMBOL` and `POOL_TOKEN_ADDRESS`, run `npm run deploy:pool`, copy the printed `SWAP_TOKEN_1_*` lines into `local.properties`, then run `npm run seed:pool`.

## Security

- Users' private keys are encrypted with the Android Keystore and EncryptedSharedPreferences.
- The buy server's treasury key lives **only** in the backend `.env`, never in the app.
- `local.properties` and `smart-contracts/.env` are ignored by Git, so secrets don't get pushed.
