const hre = require("hardhat");

async function main() {
  const [signer] = await hre.ethers.getSigners();
  const provider = hre.ethers.provider;
  const latestNonce = await provider.getTransactionCount(signer.address, "latest");
  const pendingNonce = await provider.getTransactionCount(signer.address, "pending");
  const feeData = await provider.getFeeData();

  console.log("Signer:", signer.address);
  console.log("Latest nonce:", latestNonce);
  console.log("Pending nonce:", pendingNonce);
  console.log("Max fee per gas:", feeData.maxFeePerGas?.toString() || "n/a");
  console.log("Max priority fee per gas:", feeData.maxPriorityFeePerGas?.toString() || "n/a");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
