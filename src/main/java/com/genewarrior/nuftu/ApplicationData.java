package com.genewarrior.nuftu;

import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Scope(value = "singleton", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ApplicationData {

    private Mintingcost currentMintingcost;

    private OkHttpClient client = null;

    public Mintingcost getMintingCost(EthComponent ethComponent) {
        //if older than 10min
        if (currentMintingcost == null || System.currentTimeMillis() - currentMintingcost.getLastUpdate() > 10 * 60 * 1000) {
            //update minting cost
            long currentMintingCost = ethComponent.getCurrentMintingCost();
            BigDecimal cost = new BigDecimal(currentMintingCost).movePointLeft(2);
            currentMintingcost = new Mintingcost();
            currentMintingcost.setMintingCost(cost);
            currentMintingcost.setLastUpdate(System.currentTimeMillis());
            currentMintingcost.setValid(currentMintingCost < 20000); //only valid if cost below 200 USD
        }

        return currentMintingcost;
    }

    public OkHttpClient getClient() {
        if (client == null)
            client = new OkHttpClient();
        return client;
    }


    public static class Mintingcost {
        @Getter
        @Setter
        BigDecimal mintingCost;

        @Getter
        @Setter
        long lastUpdate;

        @Getter
        @Setter
        boolean valid;
    }


}
