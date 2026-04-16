package id.rahmat.projekakhir.wallet;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.generated.Uint256;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import id.rahmat.projekakhir.BuildConfig;

public class EthereumService {

    private final Map<EthereumNetwork, Web3j> clients = new EnumMap<>(EthereumNetwork.class);
    private static final BigInteger DEFAULT_CONTRACT_GAS_LIMIT = BigInteger.valueOf(350_000L);

    public BigDecimal getBalance(String address, EthereumNetwork network) throws IOException {
        BigInteger wei = getClient(network)
                .ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();
        return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
    }

    public GasEstimation estimateNativeTransfer(String from, String to, BigDecimal amountEth,
                                                EthereumNetwork network) throws IOException {
        Web3j web3j = getClient(network);
        BigInteger value = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        Transaction transaction = Transaction.createEtherTransaction(from, null, gasPrice, null, to, value);
        BigInteger gasLimit = web3j.ethEstimateGas(transaction).send().getAmountUsed();
        if (gasLimit == null || BigInteger.ZERO.equals(gasLimit)) {
            gasLimit = BigInteger.valueOf(21000L);
        }

        BigDecimal gasFeeEth = Convert.fromWei(new BigDecimal(gasPrice.multiply(gasLimit)), Convert.Unit.ETHER);
        return new GasEstimation(gasLimit, gasPrice, gasFeeEth);
    }

    public String sendNativeTransfer(Credentials credentials, String to, BigDecimal amountEth,
                                     EthereumNetwork network) throws Exception {
        return Transfer.sendFunds(getClient(network), credentials, to, amountEth, Convert.Unit.ETHER)
                .send()
                .getTransactionHash();
    }

    public BigDecimal getErc20Balance(String walletAddress, String contractAddress, int decimals,
                                      EthereumNetwork network) throws IOException {
        Function function = new Function(
                "balanceOf",
                Arrays.asList(new Address(walletAddress)),
                Arrays.asList(new TypeReference<Uint256>() {})
        );
        String data = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(walletAddress, contractAddress, data);
        EthCall response = getClient(network).ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        List<Type> outputs = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (outputs == null || outputs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigInteger rawBalance = (BigInteger) outputs.get(0).getValue();
        return new BigDecimal(rawBalance).movePointLeft(decimals);
    }

    public int getErc20Decimals(String contractAddress, EthereumNetwork network) throws IOException {
        Function function = new Function(
                "decimals",
                Collections.emptyList(),
                Arrays.asList(new TypeReference<Uint8>() {})
        );
        String data = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(null, contractAddress, data);
        EthCall response = getClient(network).ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        List<Type> outputs = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (outputs == null || outputs.isEmpty()) {
            return 18;
        }
        BigInteger rawValue = (BigInteger) outputs.get(0).getValue();
        return rawValue.intValue();
    }

    public String getErc20Symbol(String contractAddress, EthereumNetwork network) throws IOException {
        Function function = new Function(
                "symbol",
                Collections.emptyList(),
                Arrays.asList(new TypeReference<Utf8String>() {})
        );
        String data = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(null, contractAddress, data);
        EthCall response = getClient(network).ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        List<Type> outputs = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        return String.valueOf(outputs.get(0).getValue());
    }

    public BigDecimal quoteMatsToEth(BigDecimal matsAmount, EthereumNetwork network) throws IOException {
        return quoteTokenToEth(matsAmount, 18, BuildConfig.MATS_SWAP_POOL_ADDRESS, network);
    }

    public BigDecimal quoteEthToMats(BigDecimal ethAmount, EthereumNetwork network) throws IOException {
        return quoteEthToToken(ethAmount, 18, BuildConfig.MATS_SWAP_POOL_ADDRESS, network);
    }

    public BigDecimal quoteTokenToEth(BigDecimal tokenAmount, int decimals, String poolAddress,
                                      EthereumNetwork network) throws IOException {
        BigInteger rawTokenAmount = tokenAmount.movePointRight(decimals).toBigInteger();
        BigInteger weiOut = callUint256Function(
                poolAddress,
                new Function(
                        "quoteMatsToEth",
                        Arrays.asList(new Uint256(rawTokenAmount)),
                        Arrays.asList(new TypeReference<Uint256>() {})
                ),
                network
        );
        return Convert.fromWei(new BigDecimal(weiOut), Convert.Unit.ETHER);
    }

    public BigDecimal quoteEthToToken(BigDecimal ethAmount, int decimals, String poolAddress,
                                      EthereumNetwork network) throws IOException {
        BigInteger weiAmount = Convert.toWei(ethAmount, Convert.Unit.ETHER).toBigInteger();
        BigInteger tokenOut = callUint256Function(
                poolAddress,
                new Function(
                        "quoteEthToMats",
                        Arrays.asList(new Uint256(weiAmount)),
                        Arrays.asList(new TypeReference<Uint256>() {})
                ),
                network
        );
        return new BigDecimal(tokenOut).movePointLeft(decimals);
    }

    public BigDecimal getErc20Allowance(String walletAddress, String tokenAddress, String spenderAddress,
                                        int decimals, EthereumNetwork network) throws IOException {
        BigInteger rawAllowance = callUint256Function(
                tokenAddress,
                new Function(
                        "allowance",
                        Arrays.asList(new Address(walletAddress), new Address(spenderAddress)),
                        Arrays.asList(new TypeReference<Uint256>() {})
                ),
                network
        );
        return new BigDecimal(rawAllowance).movePointLeft(decimals);
    }

    public String approveErc20(Credentials credentials, String tokenAddress, String spenderAddress,
                               BigDecimal tokenAmount, int decimals, EthereumNetwork network) throws Exception {
        BigInteger rawAmount = tokenAmount.movePointRight(decimals).toBigIntegerExact();
        Function function = new Function(
                "approve",
                Arrays.asList(new Address(spenderAddress), new Uint256(rawAmount)),
                Arrays.asList(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );
        return executeContractTransaction(credentials, tokenAddress, function, BigInteger.ZERO, network);
    }

    public String swapMatsForEth(Credentials credentials, BigDecimal matsAmount, BigDecimal minEthOut,
                                 EthereumNetwork network) throws Exception {
        return swapTokenForEth(credentials, BuildConfig.MATS_SWAP_POOL_ADDRESS, matsAmount, 18, minEthOut, network);
    }

    public String swapEthForMats(Credentials credentials, BigDecimal ethAmount, BigDecimal minTokenOut,
                                 EthereumNetwork network) throws Exception {
        return swapEthForToken(credentials, BuildConfig.MATS_SWAP_POOL_ADDRESS, ethAmount, minTokenOut, 18, network);
    }

    public String swapTokenForEth(Credentials credentials, String poolAddress, BigDecimal tokenAmount,
                                  int decimals, BigDecimal minEthOut, EthereumNetwork network) throws Exception {
        BigInteger rawTokenIn = tokenAmount.movePointRight(decimals).toBigIntegerExact();
        BigInteger rawMinEthOut = Convert.toWei(minEthOut, Convert.Unit.ETHER).toBigInteger();
        Function function = new Function(
                "swapMatsForEth",
                Arrays.asList(new Uint256(rawTokenIn), new Uint256(rawMinEthOut)),
                Arrays.asList(new TypeReference<Uint256>() {})
        );
        return executeContractTransaction(credentials, poolAddress, function, BigInteger.ZERO, network);
    }

    public String swapEthForToken(Credentials credentials, String poolAddress, BigDecimal ethAmount,
                                  BigDecimal minTokenOut, int decimals, EthereumNetwork network) throws Exception {
        BigInteger rawEthIn = Convert.toWei(ethAmount, Convert.Unit.ETHER).toBigIntegerExact();
        BigInteger rawMinTokenOut = minTokenOut.movePointRight(decimals).toBigInteger();
        Function function = new Function(
                "swapEthForMats",
                Arrays.asList(new Uint256(rawMinTokenOut)),
                Arrays.asList(new TypeReference<Uint256>() {})
        );
        return executeContractTransaction(credentials, poolAddress, function, rawEthIn, network);
    }

    private BigInteger callUint256Function(String contractAddress, Function function,
                                           EthereumNetwork network) throws IOException {
        String data = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(null, contractAddress, data);
        EthCall response = getClient(network).ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        List<Type> outputs = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (outputs == null || outputs.isEmpty()) {
            return BigInteger.ZERO;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    private String executeContractTransaction(Credentials credentials, String contractAddress, Function function,
                                              BigInteger value, EthereumNetwork network) throws Exception {
        Web3j web3j = getClient(network);
        String data = FunctionEncoder.encode(function);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = estimateGasLimit(credentials.getAddress(), contractAddress, data, value, network);
        RawTransactionManager transactionManager = new RawTransactionManager(web3j, credentials, network.getChainId());
        EthSendTransaction response = transactionManager.sendTransaction(gasPrice, gasLimit, contractAddress, data, value);
        if (response.hasError()) {
            throw new IllegalStateException(response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private BigInteger estimateGasLimit(String from, String to, String data, BigInteger value,
                                        EthereumNetwork network) throws IOException {
        Transaction estimateTransaction = Transaction.createFunctionCallTransaction(from, null, null, null, to, value, data);
        EthEstimateGas estimateGas = getClient(network).ethEstimateGas(estimateTransaction).send();
        if (estimateGas.hasError() || estimateGas.getAmountUsed() == null || BigInteger.ZERO.equals(estimateGas.getAmountUsed())) {
            return DEFAULT_CONTRACT_GAS_LIMIT;
        }
        return estimateGas.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    }

    private Web3j getClient(EthereumNetwork network) {
        Web3j existing = clients.get(network);
        if (existing != null) {
            return existing;
        }
        Web3j client = Web3j.build(new HttpService(network.getRpcUrl()));
        clients.put(network, client);
        return client;
    }
}
