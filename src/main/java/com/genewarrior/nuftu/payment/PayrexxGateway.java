package com.genewarrior.nuftu.payment;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

public class PayrexxGateway {
    public enum PaymentStatus {
        notCreated, waiting, confirmed, authorized, reserved, deleted;
    }

    @Getter
    private boolean gatewayCreated = false;

    @Getter
    private boolean gatewayDeleted = false;

    @Getter
    @Setter
    private int amount_in_cents;

    @Getter
    private final String currency = "USD";

    @Getter
    @Setter
    private String referenceId = "";

    @Getter
    @Setter
    private String purpose = "";

    @Getter
    @Setter
    private String successURL = "";

    @Getter
    @Setter
    private String failedURL = "";

    @Getter
    @Setter
    private String cancelURL = "";

    @Getter
    @Setter
    private int gatewayValidity_in_min = -1;

    @Getter
    private int paymentId = -1;

    @Getter
    private String paymentLink;

    /**
     * Potential values: waiting|confirmed|authorized|reserved
     * added values: notCreated|deleted
     */
    @Getter
    private PaymentStatus paymentStatus = PaymentStatus.notCreated;

    @Getter
    private long lastPaymentUpdate;

    HashMap<String, String> getPostdata() {
        HashMap<String, String> data = new HashMap<>();
        data.put("amount", Integer.toString(getAmount_in_cents()));
        if (!getCurrency().isEmpty())
            data.put("currency", getCurrency());
        if (!getPurpose().isEmpty())
            data.put("purpose", getPurpose());
        if (!getSuccessURL().isEmpty())
            data.put("successRedirectUrl", getSuccessURL());
        if (!getFailedURL().isEmpty())
            data.put("failedRedirectUrl", getFailedURL());
        if (!getCancelURL().isEmpty())
            data.put("cancelRedirectUrl", getCancelURL());
        if (!getReferenceId().isEmpty())
            data.put("referenceId", getReferenceId());
        if (getGatewayValidity_in_min() > 0)
            data.put("validity", Integer.toString(getGatewayValidity_in_min()));
        return data;
    }

    protected void setRetrievedData(int paymentId, String paymentLink, String paymentStatus) {
        this.paymentId = paymentId;
        this.paymentLink = paymentLink;
        this.paymentStatus = PaymentStatus.valueOf(paymentStatus);
        lastPaymentUpdate = System.currentTimeMillis();
        this.gatewayCreated = true;
    }

    protected void updateStatus(String paymentStatus) {
        this.paymentStatus = PaymentStatus.valueOf(paymentStatus);
        lastPaymentUpdate = System.currentTimeMillis();
    }

    protected void deleteGateway() {
        this.paymentStatus = PaymentStatus.deleted;
        gatewayDeleted = true;
    }

}
