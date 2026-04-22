require("dotenv").config();

const cors = require("cors");
const express = require("express");

// Some local dev environments set a broken HTTP proxy (for example 127.0.0.1:9).
// Midtrans SDK will follow those variables, so clear them for the buy server.
for (const key of ["HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "ALL_PROXY", "all_proxy"]) {
  delete process.env[key];
}

const midtransClient = require("midtrans-client");
const { ethers } = require("ethers");

const app = express();
const orders = new Map();

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

const port = Number(process.env.BUY_SERVER_PORT || 8787);
const isProduction = String(process.env.MIDTRANS_IS_PRODUCTION || "false").toLowerCase() === "true";
const devMode = String(process.env.BUY_DEV_MODE || "false").toLowerCase() === "true";
const serverKey = process.env.MIDTRANS_SERVER_KEY || "";
const clientKey = process.env.MIDTRANS_CLIENT_KEY || "";
const minIdr = Number(process.env.BUY_MIN_IDR || 1000);

const snap = new midtransClient.Snap({
  isProduction,
  serverKey,
  clientKey,
});

const coreApi = new midtransClient.CoreApi({
  isProduction,
  serverKey,
  clientKey,
});

app.get("/health", (req, res) => {
  res.json({ ok: true, service: "ethernest-buy-server" });
});

app.get("/api/price/eth-idr", async (req, res) => {
  try {
    const price = await fetchRealtimeEthIdrPrice();
    res.json({ symbol: "ETHIDR", price });
  } catch (error) {
    res.status(503).json({ message: error.message || "Harga realtime ETH/IDR belum tersedia." });
  }
});

app.post("/api/buy/eth", async (req, res) => {
  try {
    const walletAddress = String(req.body.walletAddress || "").trim();
    const amountIdr = Math.floor(Number(req.body.amountIdr || 0));
    const paymentMethod = String(req.body.paymentMethod || "all").trim().toLowerCase();

    if (!ethers.isAddress(walletAddress)) {
      return res.status(400).json({ message: "Alamat wallet tidak valid." });
    }
    if (!Number.isFinite(amountIdr) || amountIdr < minIdr) {
      return res.status(400).json({ message: `Minimal pembelian Rp ${minIdr}.` });
    }

    const estimatedEth = await calculateSepoliaEthAmount(amountIdr);
    const orderId = `ETHNEST-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
    const order = {
      orderId,
      walletAddress,
      amountIdr,
      estimatedEth,
      paymentMethod,
      status: "pending_payment",
      txHash: null,
      createdAt: new Date().toISOString(),
    };
    orders.set(orderId, order);

    if (devMode && !serverKey) {
      return res.json({
        orderId,
        estimatedEth,
        token: null,
        redirectUrl: `${req.protocol}://${req.get("host")}/checkout/${orderId}`,
      });
    }

    ensureConfiguredForOrder();

    const transaction = await snap.createTransaction({
      transaction_details: {
        order_id: orderId,
        gross_amount: amountIdr,
      },
      item_details: [
        {
          id: "SEPOLIA-ETH",
          price: amountIdr,
          quantity: 1,
          name: `${estimatedEth} Sepolia ETH`,
        },
      ],
      customer_details: {
        first_name: "Ethernest",
        email: "buyer@ethernest.local",
      },
      enabled_payments: getEnabledPayments(paymentMethod),
      custom_field1: walletAddress,
      custom_field2: estimatedEth,
    });

    res.json({
      orderId,
      estimatedEth,
      token: transaction.token,
      redirectUrl: transaction.redirect_url,
    });
  } catch (error) {
    console.error("Create buy order failed:", error);
    res.status(500).json({ message: error.message || "Order pembelian gagal dibuat." });
  }
});

app.get("/checkout/:orderId", (req, res) => {
  const order = orders.get(req.params.orderId);
  if (!order) {
    return res.status(404).send(renderCheckoutPage({
      title: "Order tidak ditemukan",
      body: "Order ini tidak ada di memory buy server. Buat ulang dari aplikasi Ethernest.",
      button: null,
    }));
  }

  res.send(renderCheckoutPage({
    title: "Checkout Ethernest",
    body: `Kamu akan membeli ${order.estimatedEth} Sepolia ETH dengan nominal Rp ${formatIdr(order.amountIdr)}. Mode ini hanya untuk testing lokal tanpa Midtrans.`,
    button: {
      label: "Bayar Sekarang",
      action: `/checkout/${order.orderId}/pay`,
    },
  }));
});

app.post("/checkout/:orderId/pay", async (req, res) => {
  try {
    if (!devMode) {
      return res.status(403).send(renderCheckoutPage({
        title: "Dev mode nonaktif",
        body: "Aktifkan BUY_DEV_MODE=true di .env untuk memakai checkout lokal.",
        button: null,
      }));
    }
    ensureConfiguredForPayout();

    const order = orders.get(req.params.orderId);
    if (!order) {
      return res.status(404).send(renderCheckoutPage({
        title: "Order tidak ditemukan",
        body: "Order ini tidak ada di memory buy server. Buat ulang dari aplikasi Ethernest.",
        button: null,
      }));
    }
    if (order.status !== "sent") {
      order.status = "paid";
      order.paidAt = new Date().toISOString();
      const txHash = await sendSepoliaEth(order.walletAddress, order.estimatedEth);
      order.status = "sent";
      order.txHash = txHash;
      order.sentAt = new Date().toISOString();
    }

    res.send(renderCheckoutPage({
      title: "Pembelian berhasil",
      body: `Sepolia ETH sudah dikirim ke wallet kamu. Tx hash: ${order.txHash}`,
      button: null,
    }));
  } catch (error) {
    console.error("Dev checkout failed:", error);
    res.status(500).send(renderCheckoutPage({
      title: "Pembelian gagal",
      body: error.message || "Buy server gagal mengirim ETH.",
      button: null,
    }));
  }
});

app.post("/api/midtrans/notification", async (req, res) => {
  try {
    ensureConfiguredForPayout();

    const notification = await coreApi.transaction.notification(req.body);
    const orderId = notification.order_id;
    const transactionStatus = notification.transaction_status;
    const fraudStatus = notification.fraud_status;
    const order = orders.get(orderId);

    if (!order) {
      return res.status(404).json({ message: "Order tidak ditemukan di memory server." });
    }

    if (isPaidStatus(transactionStatus, fraudStatus)) {
      if (order.status !== "paid" && order.status !== "sent") {
        order.status = "paid";
        order.paidAt = new Date().toISOString();
        const txHash = await sendSepoliaEth(order.walletAddress, order.estimatedEth);
        order.status = "sent";
        order.txHash = txHash;
        order.sentAt = new Date().toISOString();
      }
    } else if (["deny", "cancel", "expire", "failure"].includes(transactionStatus)) {
      order.status = transactionStatus;
    }

    res.json({ ok: true, order });
  } catch (error) {
    console.error("Midtrans notification failed:", error);
    res.status(500).json({ message: error.message || "Notifikasi Midtrans gagal diproses." });
  }
});

app.get("/api/buy/status/:orderId", (req, res) => {
  const order = orders.get(req.params.orderId);
  if (!order) {
    return res.status(404).json({ message: "Order tidak ditemukan." });
  }
  res.json(order);
});

app.post("/api/buy/test-settle/:orderId", async (req, res) => {
  try {
    if (String(process.env.BUY_ALLOW_TEST_SETTLE || "false").toLowerCase() !== "true") {
      return res.status(403).json({ message: "Endpoint test-settle belum diaktifkan." });
    }
    ensureConfiguredForPayout();

    const order = orders.get(req.params.orderId);
    if (!order) {
      return res.status(404).json({ message: "Order tidak ditemukan." });
    }
    if (order.status === "sent") {
      return res.json(order);
    }
    const txHash = await sendSepoliaEth(order.walletAddress, order.estimatedEth);
    order.status = "sent";
    order.txHash = txHash;
    order.sentAt = new Date().toISOString();
    res.json(order);
  } catch (error) {
    console.error("Test settle failed:", error);
    res.status(500).json({ message: error.message || "Test settle gagal." });
  }
});

function ensureConfiguredForOrder() {
  if (!serverKey) {
    throw new Error("MIDTRANS_SERVER_KEY belum diisi di smart-contracts/.env.");
  }
}

function ensureConfiguredForPayout() {
  if (!process.env.BUY_TREASURY_PRIVATE_KEY) {
    throw new Error("BUY_TREASURY_PRIVATE_KEY belum diisi di smart-contracts/.env.");
  }
  if (!process.env.BUY_RPC_URL && !process.env.INFURA_PROJECT_ID) {
    throw new Error("BUY_RPC_URL atau INFURA_PROJECT_ID belum diisi di smart-contracts/.env.");
  }
}

async function calculateSepoliaEthAmount(amountIdr) {
  const idrPerEth = await fetchRealtimeEthIdrPrice();
  const feePercent = Number(process.env.BUY_SPREAD_PERCENT || 0);
  const netAmountIdr = amountIdr * (1 - feePercent / 100);
  const ethAmount = netAmountIdr / idrPerEth;
  return Math.max(ethAmount, 0).toFixed(8);
}

async function fetchRealtimeEthIdrPrice() {
  const errors = [];
  try {
    return await fetchCoinGeckoEthIdrPrice();
  } catch (error) {
    errors.push(`CoinGecko: ${error.message}`);
  }

  try {
    return await fetchIndodaxEthIdrPrice();
  } catch (error) {
    errors.push(`Indodax: ${error.message}`);
  }

  try {
    return await fetchBinanceEthIdrPrice();
  } catch (error) {
    errors.push(`Binance+FX: ${error.message}`);
  }

  throw new Error(`Harga realtime ETH/IDR belum tersedia. ${errors.join(" | ")}`);
}

async function fetchCoinGeckoEthIdrPrice() {
  const response = await fetch("https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=idr");
  if (!response.ok) {
    throw new Error("Gagal mengambil harga ETH dari CoinGecko.");
  }
  const data = await response.json();
  const price = Number(data.ethereum && data.ethereum.idr);
  if (!Number.isFinite(price) || price <= 0) {
    throw new Error("Harga ETH dari CoinGecko tidak valid.");
  }
  return price;
}

async function fetchIndodaxEthIdrPrice() {
  const response = await fetch("https://indodax.com/api/ethidr/ticker");
  if (!response.ok) {
    throw new Error("Gagal mengambil harga ETH dari Indodax.");
  }
  const data = await response.json();
  const ticker = data && data.ticker ? data.ticker : {};
  const price = Number(ticker.last || ticker.buy || ticker.sell || 0);
  if (!Number.isFinite(price) || price <= 0) {
    throw new Error("Harga ETH dari Indodax tidak valid.");
  }
  return price;
}

async function fetchBinanceEthIdrPrice() {
  const ethUsdResponse = await fetch("https://api.binance.com/api/v3/ticker/price?symbol=ETHUSDT");
  if (!ethUsdResponse.ok) {
    throw new Error("Gagal mengambil harga ETHUSDT dari Binance.");
  }
  const ethUsdData = await ethUsdResponse.json();
  const ethUsd = Number(ethUsdData.price || 0);
  if (!Number.isFinite(ethUsd) || ethUsd <= 0) {
    throw new Error("Harga ETHUSDT dari Binance tidak valid.");
  }

  const usdIdrResponse = await fetch("https://open.er-api.com/v6/latest/USD");
  if (!usdIdrResponse.ok) {
    throw new Error("Gagal mengambil kurs USD/IDR.");
  }
  const usdIdrData = await usdIdrResponse.json();
  const usdIdr = Number(usdIdrData.rates && usdIdrData.rates.IDR);
  if (!Number.isFinite(usdIdr) || usdIdr <= 0) {
    throw new Error("Kurs USD/IDR tidak valid.");
  }

  return ethUsd * usdIdr;
}

async function sendSepoliaEth(walletAddress, amountEth) {
  const rpcUrl = process.env.BUY_RPC_URL || `https://sepolia.infura.io/v3/${process.env.INFURA_PROJECT_ID}`;
  const provider = new ethers.JsonRpcProvider(rpcUrl);
  const treasury = new ethers.Wallet(normalizePrivateKey(process.env.BUY_TREASURY_PRIVATE_KEY), provider);

  const tx = await treasury.sendTransaction({
    to: walletAddress,
    value: ethers.parseEther(amountEth),
  });
  await tx.wait(1);
  return tx.hash;
}

function normalizePrivateKey(privateKey) {
  const value = String(privateKey || "").trim();
  return value.startsWith("0x") ? value : `0x${value}`;
}

function isPaidStatus(transactionStatus, fraudStatus) {
  if (transactionStatus === "settlement") {
    return true;
  }
  return transactionStatus === "capture" && (!fraudStatus || fraudStatus === "accept");
}

function getEnabledPayments(paymentMethod) {
  switch (paymentMethod) {
    case "qris":
      return ["qris"];
    case "ewallet":
      return ["gopay", "shopeepay"];
    case "bank_transfer":
      return ["bank_transfer", "echannel", "permata_va", "bca_va", "bni_va", "bri_va"];
    case "retail":
      return ["cstore"];
    case "all":
    default:
      return ["qris", "gopay", "shopeepay", "bank_transfer", "echannel", "permata_va", "bca_va", "bni_va", "bri_va", "cstore"];
  }
}

function formatIdr(value) {
  return new Intl.NumberFormat("id-ID").format(value);
}

function renderCheckoutPage({ title, body, button }) {
  const action = button ? `<form method="post" action="${button.action}"><button type="submit">${button.label}</button></form>` : "";
  return `<!doctype html>
<html lang="id">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${escapeHtml(title)}</title>
  <style>
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background: #000;
      color: #f8fafc;
      font-family: Arial, sans-serif;
    }
    main {
      width: min(88vw, 420px);
      padding: 28px;
      border: 1px solid rgba(56, 189, 248, .38);
      border-radius: 28px;
      background: linear-gradient(145deg, #111827, #050505);
      box-shadow: 0 24px 80px rgba(56, 189, 248, .12);
    }
    .badge {
      display: inline-flex;
      padding: 8px 12px;
      border-radius: 999px;
      background: rgba(56, 189, 248, .14);
      color: #38bdf8;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: .08em;
      text-transform: uppercase;
    }
    h1 {
      margin: 18px 0 10px;
      font-size: 30px;
      line-height: 1.08;
    }
    p {
      margin: 0;
      color: #b8c0cc;
      font-size: 15px;
      line-height: 1.55;
      word-break: break-word;
    }
    button {
      width: 100%;
      height: 56px;
      margin-top: 24px;
      border: 0;
      border-radius: 18px;
      background: #38bdf8;
      color: #000;
      font-size: 16px;
      font-weight: 800;
    }
  </style>
</head>
<body>
  <main>
    <div class="badge">Ethernest Checkout</div>
    <h1>${escapeHtml(title)}</h1>
    <p>${escapeHtml(body)}</p>
    ${action}
  </main>
</body>
</html>`;
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

app.listen(port, () => {
  console.log(`Ethernest buy server listening on http://localhost:${port}`);
  console.log(`Midtrans mode: ${isProduction ? "production" : "sandbox"}`);
  console.log(`Dev checkout: ${devMode ? "enabled" : "disabled"}`);
});
