const hre = require("hardhat");

async function main() {
  const [signer] = await hre.ethers.getSigners();
  const provider = hre.ethers.provider;
  const latestNonce = await provider.getTransactionCount(signer.address, "latest");
  const pendingNonce = await provider.getTransactionCount(signer.address, "pending");

  console.log("Signer:", signer.address);
  console.log("Checking nonces from", latestNonce, "to", pendingNonce - 1);

  for (let nonce = latestNonce; nonce < pendingNonce; nonce += 1) {
    const address = hre.ethers.getCreateAddress({
      from: signer.address,
      nonce
    });
    const code = await provider.getCode(address);
    console.log("Nonce", nonce, "->", address, "deployed:", code && code !== "0x");
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
