package com.genewarrior.nuftu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genewarrior.nuftu.database.TransactionRecord;
import com.genewarrior.nuftu.database.TransactionRecordRepository;
import com.genewarrior.nuftu.soliditycontracts.Nuftu;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.sql.Date;

@Component
@Scope(value = "singleton", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class EthComponent {
    Logger logger = LoggerFactory.getLogger(EthComponent.class);

    @Autowired
    TransactionRecordRepository transactionRecordRepository;

    @Value("${eth.httpservice}")
    @Getter
    private String ethHttpService;

    @Value("${eth.contractadress}")
    @Getter
    private String ethContractaddress;

    @Value("${eth.privatekey}")
    private String privatekey;

    @Value("${eth.gaslimit.mint}")
    private Integer gasLimitInt;

    @Value("${eth.etherscan.api}")
    private String etherscanGaspriceUrl;

    Web3j web3;
    Credentials credentials;
    BigInteger gasLimit;

    @PostConstruct
    public void postConstruct() {
        web3 = Web3j.build(new HttpService(ethHttpService));
        credentials = Credentials.create(privatekey);
    }

    public ECKeyPair createNewKey() {
        try {
            return Keys.createEcKeyPair();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            logger.error("Error Creating New Ec Key Pair");
            logger.error("Cannot create Ec key pair", e);
            throw new ExceptionHandling.EthException("Error Creating new key", "");
        }
    }

    /**
     * Recommended Gas price from Etherscan in Gwei (see https://etherscan.io/gastracker)
     *
     * @return Recommended Gas price in Gwei
     */
    public int getRecommendedGaspriceInGwei() {
        try {
            JsonNode rootNode = (new ObjectMapper()).readTree(new URL(etherscanGaspriceUrl).openStream());
            int gasPrice = rootNode.path("result").path("ProposeGasPrice").asInt(-1);
            if (gasPrice < 0) {
                throw new Exception();
            }
            return gasPrice + 2;
        } catch (Exception e) {
            logger.error("Error getting Gas Price; IOException (cannot connect?)");
            logger.error("Error getting Gas Price", e);
            throw new ExceptionHandling.EthException("Error getting gas price", "");
        }
    }

    private Nuftu getContract() throws IOException {
        return getContract(credentials);
    }

    private Nuftu getContract(Credentials credentials) throws IOException {
        BigInteger gasPriceInWei = BigInteger.valueOf(getRecommendedGaspriceInGwei() * 1_000_000_000L);
        this.gasLimit = BigInteger.valueOf(gasLimitInt);
        final ContractGasProvider gasProvider = new StaticGasProvider(gasPriceInWei, gasLimit);
        return Nuftu.load(ethContractaddress, web3, credentials, gasProvider);
    }

    public long mint(String address) {
        try {
            Nuftu contract = getContract();
            //update gas provider
            long gasPriceInWei = getRecommendedGaspriceInGwei() * 1_000_000_000L;
            final ContractGasProvider gasProvider = new StaticGasProvider(BigInteger.valueOf(gasPriceInWei), gasLimit);
            contract.setGasProvider(gasProvider);

            //Do the deed
            TransactionReceipt transactionReceipt = contract.mint(address).send(); //blocking call
            BigInteger tokenId = contract.getTransferEvents(transactionReceipt).get(0).tokenId;

            //Record everything
            TransactionRecord transactionRecord = new TransactionRecord();
            transactionRecord.setUploadDate(new Date(System.currentTimeMillis()));
            transactionRecord.setEthHttpService(ethHttpService);
            transactionRecord.setEthContractaddress(ethContractaddress);
            transactionRecord.setEthMyAdress(credentials.getAddress());
            transactionRecord.setTransactionType(TransactionRecord.TransactionType.MINT);
            transactionRecord.setAddress(address);
            //transactionRecord.setPrivateKey(privatekey);
            transactionRecord.setGaslimit(gasLimit.longValue());
            transactionRecord.setGasCost(transactionReceipt.getGasUsed().longValue());
            transactionRecord.setGasPrice(gasPriceInWei);
            transactionRecord.setReceiveraddress("");
            transactionRecord.setTokenid(tokenId.longValue());
            transactionRecord.setTransactionreceipt(transactionReceipt.toString());
            transactionRecord.setTransactionhash(transactionReceipt.getTransactionHash());

            transactionRecordRepository.saveAndFlush(transactionRecord);

            return tokenId.longValue();


        } catch (Exception e) {
            logger.error("Error while minting [address: " + address + "]", e);
        }

        return -1;

    }

    public String getCurrentOwner(long token) {
        Nuftu contract = null;
        try {
            contract = getContract();
            return contract.ownerOf(BigInteger.valueOf(token)).send();
        } catch (Exception e) {
            logger.error("Cannot get current owner");
            logger.error("cannot get current owner", e);
        }
        return null;
    }

    /**
     * @return current price of minting in us cents
     */
    public int getCurrentMintingCost() {
        /**
         * 1 Gwei = 10^9 Wei
         * 1 Ether = 10^9 Gwei
         */

        //TODO: change pricing here
        long gascost = 180_000L; //gas cost for minting, in gwei
        int gasprice = getRecommendedGaspriceInGwei(); //in gwei
        long gas = gascost * gasprice; // in gwei
        long eth_in_usd_cents = 269000; //price of single ether = 1_000_000_000 gwei in usd cents
        long cost = gas * eth_in_usd_cents / 1_000_000_000L;
        final int steps = 143; //in cents
        int finalcost = (int) (Math.ceil(((double) cost + 200d) / ((double) steps)) * steps);
        return finalcost;
    }

    public TransferResponse transfer(String fromPrivateKey, String toAddress, long token) {
        Nuftu contract = null;
        TransferResponse response = new TransferResponse();
        try {
            Credentials fromCredentials = Credentials.create(fromPrivateKey);

            String fromAddress = getCurrentOwner(token);

            if (fromAddress == null) {
                response.error = true;
                response.errorMessage = "Cannot get owner of this NFT";
                return response;
            }

            if (!fromAddress.equalsIgnoreCase(fromCredentials.getAddress())) { //wrong private key
                response.error = true;
                response.errorMessage = "Private key is not matching owner";
                return response;
            }

            contract = getContract(fromCredentials); //call contract from the passed private key credentials

            TransactionReceipt tr = contract.safeTransferFrom(fromCredentials.getAddress(), toAddress, BigInteger.valueOf(token)).send(); //blocking call
        } catch (Exception e) {
            logger.error("Transfer error");
            logger.error("Transfer error", e);
            response.setError(true);
            response.setErrorMessage("Failed calling transfer Method");
        }
        return null;
    }

    public static class TransferResponse {
        @Getter
        @Setter
        boolean error = false;

        @Getter
        @Setter
        String errorMessage = "";
    }


}
