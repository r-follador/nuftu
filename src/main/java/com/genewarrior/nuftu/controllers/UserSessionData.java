package com.genewarrior.nuftu.controllers;

import com.genewarrior.nuftu.ApplicationData;
import com.genewarrior.nuftu.EthComponent;
import com.genewarrior.nuftu.payment.PaymentTools;
import com.genewarrior.nuftu.payment.PayrexxGateway;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserSessionData {
    @Autowired
    EthComponent ethComponent;

    @Autowired
    ApplicationData applicationData;

    @Getter
    @Setter
    private UploadAsset uploadAsset;

    @Getter
    @Setter
    private boolean validatedEntry;

    @Getter
    @Setter
    private boolean confirmedEntry;

    @Getter
    private Credentials myCredentials;

    @Getter
    private boolean newKeyCreated;

    @Getter
    @Setter
    private boolean paymentStarted = false;

    @Getter
    @Setter
    private long tokennumber = -1;

    @Getter
    @Setter
    private boolean hasPaymentError = false;

    @Getter
    @Setter
    private String paymentErrorMessage = "";

    @Getter
    private PayrexxGateway payrexxGateway = null;

    @Getter
    private String uuid = UUID.randomUUID().toString();

    public void reset() {
        validatedEntry = false;
        confirmedEntry = false;
        if (uploadAsset != null && uploadAsset.getFile() != null) {
            uploadAsset.getAssetFile().delete();
        }
        uploadAsset = null;
        myCredentials = null;
        newKeyCreated = false;
        tokennumber = -1;
        resetPaymentError();
        hasPaymentError = false;
        uuid = UUID.randomUUID().toString();
        payrexxGateway = null;
        paymentStarted = false;
    }

    public void setPaymentError(String message) {
        setHasPaymentError(true);
        setPaymentErrorMessage(message);
    }

    public void resetPaymentError() {
        setHasPaymentError(false);
        setPaymentErrorMessage("");
    }

    public void generateKeypair() {
        if (!getUploadAsset().getAddress().isEmpty()) {
            newKeyCreated = false;
        } else {
            if (!newKeyCreated) { //if newKeyCreated==true then a key has already been created (likely page reload)
                ECKeyPair myKeypair = ethComponent.createNewKey();
                myCredentials = Credentials.create(myKeypair);
                newKeyCreated = true;
            }
        }
    }

    public String getPrivateKey() {
        if (newKeyCreated)
            return myCredentials.getEcKeyPair().getPrivateKey().toString(16);
        return null;
    }

    public String getPublicKey() {
        if (newKeyCreated)
            return myCredentials.getEcKeyPair().getPublicKey().toString(16);
        return null;
    }

    public String getAddress() {
        if (newKeyCreated)
            return myCredentials.getAddress();
        else
            return uploadAsset.getAddress();
    }

    public String generatePayrexxGateway() throws PaymentTools.PaymentToolException, IOException {
        payrexxGateway = new PayrexxGateway();
        payrexxGateway.setReferenceId(getUuid());
        payrexxGateway.setAmount_in_cents(ethComponent.getCurrentMintingCost());
        payrexxGateway.setCancelURL("https://nuftu.com/create?action=paymentfailure");
        payrexxGateway.setFailedURL("https://nuftu.com/create?action=paymentfailure");
        payrexxGateway.setSuccessURL("https://nuftu.com/mint/" + getUuid());
        PaymentTools.createGateway(payrexxGateway, applicationData.getClient());
        return payrexxGateway.getPaymentLink();
    }

    public PayrexxGateway.PaymentStatus getUpdatedPaymentStatus() throws PaymentTools.PaymentToolException, IOException {
        if (payrexxGateway == null) {
            System.out.println("---gateway is null");
            return PayrexxGateway.PaymentStatus.notCreated;
        }

        PaymentTools.refreshGateway(payrexxGateway, applicationData.getClient());
        System.out.println("---payrexx id: " + payrexxGateway.getPaymentId());
        System.out.println("---payrexx status: " + payrexxGateway.getPaymentStatus());
        return payrexxGateway.getPaymentStatus();
    }

    @PreDestroy
    public void preDestroy() {
        reset();
    }
}
