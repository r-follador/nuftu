package com.genewarrior.nuftu.controllers;

import com.genewarrior.nuftu.ApplicationData;
import com.genewarrior.nuftu.EthComponent;
import com.genewarrior.nuftu.ExceptionHandling;
import com.genewarrior.nuftu.database.Metadata;
import com.genewarrior.nuftu.database.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MintingService {
    @Autowired
    private MetadataRepository metadataRepository;

    @Autowired
    private ApplicationData applicationData;

    @Autowired
    private EthComponent ethComponent;

    Logger logger = LoggerFactory.getLogger(MintingService.class);

    @Async("asyncExecutor")
    public void mint(String uuid) {

        /**
         * check if minting has started already, then return directly
         * if not get asset from database
         * set status to startminting,
         * start minting, if okay set status to valid and add token number in database
         *
         * do not rely on usersessiondata, only on database entry
         */

        Metadata asset = (new MintingTransaction()).getAsset(uuid);

        if (asset.getTokenState() == Metadata.TokenState.MINTING)
            return;

        (new MintingTransaction()).setAssetState(uuid, Metadata.TokenState.MINTING);

        logger.info("-- start minting [Asset UUID:" + asset.getUuid() + " SHA: " + asset.getSha256() + "]");

        long tokennumber = ethComponent.mint(asset.getCreator());

        if (tokennumber == -1) {
            logger.error("@@@@@@@@@@@@ MINTING ERROR @@@@@@@@@@@@@@");
            logger.error("Error minting token; UUID " + uuid + " ; payment_id: " + asset.getPayment_id());
            (new MintingTransaction()).setAssetState(uuid, Metadata.TokenState.MINTING_ERROR);
            throw new ExceptionHandling.EthException("Could not create Token", "Report following ID to follow up on this error: " + uuid);
        }

        (new MintingTransaction()).setTokennumberAndState(uuid, tokennumber, Metadata.TokenState.VALID);

        logger.info("Minting successful: Tokennumber: " + tokennumber + " [Asset UUID:" + uuid + "]");
    }

    public class MintingTransaction {
        @Transactional
        public void setAssetState(String uuid, Metadata.TokenState tokenState) {
            metadataRepository.setTokenstateByUuid(tokenState, uuid);
        }

        @Transactional
        public void setTokennumberAndState(String uuid, long tokennumber, Metadata.TokenState tokenState) {
            metadataRepository.setTokenstateAndTokennumberByUuid(tokenState, tokennumber, uuid);
        }

        @Transactional(readOnly = true)
        public Metadata getAsset(String uuid) {
            return metadataRepository.findByUuid(uuid).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This UUID number does not exist; Report ID: " + uuid));
        }
    }


}
