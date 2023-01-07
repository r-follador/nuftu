package com.genewarrior.nuftu.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genewarrior.nuftu.ApplicationData;
import com.genewarrior.nuftu.EthComponent;
import com.genewarrior.nuftu.ExceptionHandling;
import com.genewarrior.nuftu.database.Metadata;
import com.genewarrior.nuftu.database.MetadataRepository;
import com.genewarrior.nuftu.payment.PaymentTools;
import com.genewarrior.nuftu.payment.PayrexxGateway;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.io.IOException;
import java.sql.Date;
import java.util.Optional;

@Controller
public class CreateController {
    @Autowired
    private MetadataRepository metadataRepository;

    @Autowired
    private UserSessionData userSessionData;

    @Autowired
    private ApplicationData applicationData;

    @Autowired
    private EthComponent ethComponent;

    @Autowired
    private MintingService mintingService;

    Logger logger = LoggerFactory.getLogger(CreateController.class);

    @Transactional
    @GetMapping(value = "/mint/{uuid}")
    public String checkOrStartMintingStatus(@PathVariable("uuid") String uuid, Model model) {
        Metadata asset = metadataRepository.findByUuid(uuid).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This UUID number does not exist; Report ID: " + userSessionData.getUuid()));

        //System.out.println("Token: "+uuid+ " token state: "+asset.getTokenState().toString());
        if (asset.getTokenState() == Metadata.TokenState.VALID) {
            if (uuid.equals(userSessionData.getUuid())) { //if usersession matches called uuid
                userSessionData.setTokennumber(asset.getTokennumber());
                return showFinal(userSessionData, model);
            } else {
                userSessionData.reset();
                return "redirect:/nft/" + asset.getTokennumber().toString();
            }
        } else if (asset.getTokenState() == Metadata.TokenState.PAYMENT_WAITING) {
            //check status again on payrexx; if okay start minting, if not go to payment page
            try {
                PayrexxGateway.PaymentStatus status = PaymentTools.getGatewayStatus(asset.getPayment_id(), applicationData.getClient());
                if (status == PayrexxGateway.PaymentStatus.confirmed) {
                    mintingService.mint(asset.getUuid());
                    return "minting_status";
                } else if (status == PayrexxGateway.PaymentStatus.waiting) {
                    String url = PaymentTools.getPaymentUrlFromId(asset.getPayment_id(), applicationData.getClient());
                    return "redirect:" + url;
                }
            } catch (IOException | PaymentTools.PaymentToolException e) {
                //silently fail
                logger.warn("Can't get Payment status, payment id: " + asset.getPayment_id(), e);
            }
        } else if (asset.getTokenState() == Metadata.TokenState.MINTING) {
            model.addAttribute("Status", "Your Token is being minted.");
            return "minting_status";
        } else if (asset.getTokenState() == Metadata.TokenState.MINTING_ERROR) {
            model.addAttribute("errorminting", true);
            model.addAttribute("errormintingmessage", "An Error occured while minting your token! Please contact support immediately, report following ID: " + uuid);
            return "minting_status";
        }
        return "";
    }

    @GetMapping("/create")
    @Transactional
    public String create(UploadAsset uploadAsset, @RequestParam(name = "action", required = false) String action, Model model) {
        if (userSessionData == null || !userSessionData.isValidatedEntry()) //this is where it starts
            return "create_1";
        else if (userSessionData.isValidatedEntry() && !userSessionData.isConfirmedEntry()) { //after post data
            if (action == null || action.isEmpty()) { //display create_2 with all the uploaded data to confirm
                model.addAttribute("name", userSessionData.getUploadAsset().getName());
                model.addAttribute("description", userSessionData.getUploadAsset().getDescription());
                model.addAttribute("mimetype", userSessionData.getUploadAsset().getMimetype());
                model.addAttribute("sha256", userSessionData.getUploadAsset().getSha256());
                return "create_2";
            } else {
                if (action.equalsIgnoreCase("discard")) //no confirmed, discarded
                    userSessionData.reset();
                else if (action.equalsIgnoreCase("confirm")) { //confirmed
                    userSessionData.setConfirmedEntry(true);
                }
                return "redirect:/create"; //either start over or confirmedentry -> create_3
            }
        } else if (userSessionData.isValidatedEntry() && userSessionData.isConfirmedEntry()) {

            //System.out.println("validated and confirmed");

            if (action != null && action.equalsIgnoreCase("paymentfailure"))
                userSessionData.setPaymentStarted(false);

            if (userSessionData.isPaymentStarted()) { //payment ongoing or finished
                //System.out.println("payment is ongoing or finished");
                try {
                    PaymentTools.refreshGateway(userSessionData.getPayrexxGateway(), applicationData.getClient());
                    if (userSessionData.getPayrexxGateway().getPaymentStatus() == PayrexxGateway.PaymentStatus.waiting) { //payment ongoing
                        //System.out.println("Payment ongoing");
                        return "redirect:" + userSessionData.getPayrexxGateway().getPaymentLink();
                    } else if (userSessionData.getPayrexxGateway().getPaymentStatus() == PayrexxGateway.PaymentStatus.confirmed) { //payment finished
                        Metadata asset = metadataRepository.findByUuid(userSessionData.getUuid()).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This UUID number does not exist; Report ID: " + userSessionData.getUuid()));
                        //System.out.println("Payment finished");
                        if (asset.getTokenState() == Metadata.TokenState.VALID) {
                            //System.out.println("Token state valid");
                            return showFinal(userSessionData, model);
                        } else {
                            //System.out.println("Token state "+asset.getTokenState().toString());
                            return "redirect:/mint/" + userSessionData.getUuid(); //check minting status
                        }
                    }
                } catch (IOException | PaymentTools.PaymentToolException e) {
                    logger.error("Error creating Payment Gateway; UUID " + userSessionData.getUuid());
                    logger.error("Error creating Payment Gateway", e);
                    throw new ExceptionHandling.EthException("Error creating Payment link", "Report following UUID to follow up on this error: " + userSessionData.getUuid());
                }
            }

            if (action == null || action.isEmpty()) {
                //System.out.println("no action");
                userSessionData.generateKeypair();
                model.addAttribute("newkeycreated", userSessionData.isNewKeyCreated());
                if (userSessionData.isNewKeyCreated()) {
                    model.addAttribute("privatekey", userSessionData.getPrivateKey());
                    model.addAttribute("publickey", userSessionData.getPublicKey());
                    model.addAttribute("address", userSessionData.getAddress());
                } else {
                    model.addAttribute("address", userSessionData.getAddress());
                }
                if (userSessionData.isHasPaymentError()) {
                    model.addAttribute("errorpaying", true);
                    model.addAttribute("errorpayingmessage", userSessionData.getPaymentErrorMessage());
                } else {
                    model.addAttribute("errorpaying", false);
                }
                return "create_3";
            } else if (action.equalsIgnoreCase("mint")) {
                //System.out.println("action: mint");
                userSessionData.setHasPaymentError(false);

                //check if metadata is already saved in database
                //this can happen if "action: paymentfailure" is called beforehand, in this case update existing
                Optional<Metadata> result = metadataRepository.findByUuid(userSessionData.getUuid());
                Metadata metadata = result.orElseGet(Metadata::new);

                System.out.println("--- metadata: " + metadata.getUuid());

                metadata.setCreator(userSessionData.getAddress());
                try {
                    metadata.setFile(FileUtils.readFileToByteArray(userSessionData.getUploadAsset().getAssetFile()));
                    if (userSessionData.getUploadAsset().getThumbnail() != null)
                        metadata.setThumbnail(userSessionData.getUploadAsset().getThumbnail());
                } catch (IOException e) {
                    logger.error("Error saving metadata to database (file to byte[]); UUID " + userSessionData.getUuid());
                    logger.error("Error saving metadata to database", e);
                    userSessionData.setPaymentStarted(false);
                    throw new ExceptionHandling.EthException("Error saving to database", "Report following UUID to follow up on this error: " + userSessionData.getUuid());
                }

                try {
                    String url = userSessionData.generatePayrexxGateway();
                    metadata.setName(userSessionData.getUploadAsset().getName());
                    metadata.setDescription(userSessionData.getUploadAsset().getDescription());
                    metadata.setMediatype(userSessionData.getUploadAsset().getMimetype());
                    metadata.setSha256(userSessionData.getUploadAsset().getSha256());
                    metadata.setUploadDate(new Date(System.currentTimeMillis()));
                    metadata.setUuid(userSessionData.getUuid());
                    metadata.setEmail(userSessionData.getUploadAsset().getEmail());
                    metadata.setTokenState(Metadata.TokenState.PAYMENT_WAITING);
                    metadata.setTokennumber(-1L);
                    metadata.setPayment_id(userSessionData.getPayrexxGateway().getPaymentId());
                    metadataRepository.saveAndFlush(metadata);
                    userSessionData.setPaymentStarted(true);
                    //System.out.println("metadata saved");
                    //System.out.println("redirect to "+url);
                    return "redirect:" + url;
                } catch (PaymentTools.PaymentToolException | IOException e) {
                    logger.error("Error creating Payment Gateway; UUID " + userSessionData.getUuid());
                    logger.error("Error creating Payment Gateway", e);
                    throw new ExceptionHandling.EthException("Error creating Payment link", "Report following UUID to follow up on this error: " + userSessionData.getUuid());
                }
            } else if (action.equalsIgnoreCase("discard")) {
                //System.out.println("action: discard");
                userSessionData.reset();
                return "redirect:/create";
            } else if (action.equalsIgnoreCase("paymentfailure")) {
                //System.out.println("action: payment failure");
                userSessionData.setHasPaymentError(true);
                userSessionData.setPaymentErrorMessage("Payment has failed. Please try again.");
                return "redirect:/create";
            }

        }
        return "error";
    }

    @PostMapping(value = "/create")
    public String uploadFile(@Valid UploadAsset uploadAsset, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "create_1";
        }

        uploadAsset.check();

        if (uploadAsset.hasFileError || uploadAsset.hasAddressError) {
            if (uploadAsset.hasFileError) {
                bindingResult.addError(new FieldError("uploadAsset", "file", uploadAsset.getFileErrorMessage()));
            }
            if (uploadAsset.hasAddressError)
                bindingResult.addError(new FieldError("uploadAsset", "address", uploadAsset.getAddressErrorMessage()));
            return "create_1";
        }

        userSessionData.setUploadAsset(uploadAsset);
        userSessionData.setValidatedEntry(true);

        return "redirect:/create"; //will go to create_2 in GetMapping(/create)

    }

    private String showFinal(UserSessionData userSessionData, Model model) {


        model.addAttribute("newkeycreated", userSessionData.isNewKeyCreated());
        if (userSessionData.isNewKeyCreated()) {
            model.addAttribute("privatekey", userSessionData.getPrivateKey());
            model.addAttribute("publickey", userSessionData.getPublicKey());
            model.addAttribute("address", userSessionData.getAddress());
        } else {
            model.addAttribute("address", userSessionData.getAddress());
        }
        model.addAttribute("name", userSessionData.getUploadAsset().getName());
        model.addAttribute("description", userSessionData.getUploadAsset().getDescription());
        model.addAttribute("mimetype", userSessionData.getUploadAsset().getMimetype());
        model.addAttribute("sha256", userSessionData.getUploadAsset().getSha256());
        model.addAttribute("token", userSessionData.getTokennumber());
        model.addAttribute("contractaddress", ethComponent.getEthContractaddress());

        return "minting_success";
    }

    @GetMapping(value = "/create2_file")
    public ResponseEntity<byte[]> returnFile() throws IOException {
        if (userSessionData == null || !userSessionData.isValidatedEntry())
            return null;

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Content-Type", userSessionData.getUploadAsset().getMimetype());
        byte[] out = FileUtils.readFileToByteArray(userSessionData.getUploadAsset().getAssetFile());
        return new ResponseEntity<>(out, responseHeaders, HttpStatus.CREATED);
    }

    @PostMapping(value = "/payment_hook")
    public void paymentHook(@RequestBody String payload) {
        //curl --header "Content-Type: application/json" --request POST --data '{"username":"xyz","password":"xyz"}' http://localhost:8080/payment_hook
        logger.info("Payment_hook called: " + payload);
        try {
            JsonNode jsonNode = (new ObjectMapper()).readTree(payload);
            String uuid = jsonNode.path("transaction").path("referenceId").asText("blarg");
            if (uuid.equals("blarg")) {
                logger.warn("Payment_hook: cannot extract UUID from Json");
                return;
            }

            Metadata asset = metadataRepository.findByUuid(uuid).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This UUID number does not exist; Report ID: " + uuid));

            try {
                PayrexxGateway.PaymentStatus status = PaymentTools.getGatewayStatus(asset.getPayment_id(), applicationData.getClient());
                if (status == PayrexxGateway.PaymentStatus.confirmed && asset.getTokenState() == Metadata.TokenState.PAYMENT_WAITING) {
                    mintingService.mint(asset.getUuid());
                }
            } catch (IOException | PaymentTools.PaymentToolException e) {
                //silently fail
                logger.warn("Can't get Payment status, payment id: " + asset.getPayment_id(), e);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        throw new ResponseStatusException(HttpStatus.OK, "all good");
    }

}