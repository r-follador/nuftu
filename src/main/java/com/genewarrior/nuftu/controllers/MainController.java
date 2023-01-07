package com.genewarrior.nuftu.controllers;

import com.genewarrior.nuftu.ApplicationData;
import com.genewarrior.nuftu.EthComponent;
import com.genewarrior.nuftu.ExceptionHandling;
import com.genewarrior.nuftu.database.Metadata;
import com.genewarrior.nuftu.database.MetadataRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.List;

@Controller
public class MainController {
    @Autowired
    private MetadataRepository metadataRepository;

    @Autowired
    private UserSessionData userSessionData;

    @Autowired
    private ApplicationData applicationData;

    @Autowired
    private EthComponent ethComponent;

    @GetMapping("/index")
    public String index() {
        return "index";
    }

    @GetMapping("/how_it_works")
    public String how_it_works() {
        return "how_it_works";
    }

    @GetMapping("/how_to_sell")
    public String how_to_sell() {
        return "how_to_sell";
    }

    @GetMapping("/tos")
    public String terms_of_service() {
        return "tos";
    }

    @GetMapping("/pricing")
    public String pricing() {
        return "pricing";
    }

    @GetMapping("/find")
    public String find(Model model) {
        List<Metadata> tokens = metadataRepository.findByTokenState(Metadata.TokenState.VALID);
        Collections.shuffle(tokens);
        model.addAttribute("tokens", tokens);
        return "find";
    }

    Logger logger = LoggerFactory.getLogger(MainController.class);

    @Transactional(readOnly = true)
    @ResponseBody
    @GetMapping(value = "/api/{token}", produces = "application/json")
    public Metadata getMetadata(@PathVariable("token") long tokennumber) {
        Metadata metadata = metadataRepository.findByTokennumber(tokennumber).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This Token number does not exist"));
        if (metadata.getTokenState() == Metadata.TokenState.LOCKED) {
            throw new ExceptionHandling.NFTnotExistingException("This Token is locked!");
        }
        return metadata;
    }

    @Transactional(readOnly = true)
    @GetMapping(value = "/api/file/{token}")
    public ResponseEntity<byte[]> returnFileApi(@PathVariable("token") long tokennumber) {

        logger.info("Show token: " + tokennumber);
        Metadata metadata = metadataRepository.findByTokennumber(tokennumber).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This Token number does not exist"));
        if (metadata.getTokenState() == Metadata.TokenState.LOCKED) {
            throw new ExceptionHandling.NFTnotExistingException("This Token is locked!");
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Content-Type", metadata.getMediatype());
        return new ResponseEntity<byte[]>(metadata.getFile(), responseHeaders, HttpStatus.OK);
    }

    @Transactional(readOnly = true)
    @GetMapping(value = "/api/thumbnail/{token}")
    public ResponseEntity<byte[]> returnThumbnailApi(@PathVariable("token") long tokennumber) {
        Metadata metadata = metadataRepository.findByTokennumber(tokennumber).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This Token number does not exist"));
        if (metadata.getTokenState() == Metadata.TokenState.LOCKED) {
            throw new ExceptionHandling.NFTnotExistingException("This Token is locked!");
        }
        if (metadata.getThumbnail() == null || metadata.getThumbnail().length < 10)
            return returnFileApi(tokennumber);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Content-Type", "image/jpeg");
        return new ResponseEntity<byte[]>(metadata.getThumbnail(), responseHeaders, HttpStatus.OK);
    }

    @Value("${eth.etherscan.link}")
    private String outlink_etherscan;

    @Value("${eth.opensea.link}")
    private String outlink_opensea;

    @Value("${eth.rarible.link}")
    private String outlink_rarible;

    @Transactional(readOnly = true)
    @GetMapping("/nft/{token}")
    public String showNFT(@PathVariable("token") long tokennumber, Model model) {
        Metadata metadata = metadataRepository.findByTokennumber(tokennumber).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This Token number does not exist"));
        if (metadata.getTokenState() == Metadata.TokenState.LOCKED) {
            throw new ExceptionHandling.NFTnotExistingException("This Token is locked!");
        }

        String full_outlink_etherscan = String.format(outlink_etherscan, tokennumber);
        String full_outlink_opensea = String.format(outlink_opensea, tokennumber);
        String full_outlink_rarible = String.format(outlink_rarible, tokennumber);

        model.addAttribute("token", tokennumber);
        model.addAttribute("name", metadata.getName());
        model.addAttribute("description", metadata.getDescription());
        model.addAttribute("mimetype", metadata.getMediatype());
        model.addAttribute("sha256", metadata.getSha256());
        model.addAttribute("creator", metadata.getCreator());
        model.addAttribute("contractaddress", ethComponent.getEthContractaddress());
        model.addAttribute("outlink_etherscan", full_outlink_etherscan);
        model.addAttribute("outlink_opensea", full_outlink_opensea);
        model.addAttribute("outlink_rarible", full_outlink_rarible);


        return "tokenview";

    }

    @Transactional(readOnly = true)
    @ResponseBody
    @GetMapping(value = "/eth/{token}", produces = "application/json")
    public EthOwnerResponse getTokenOwner(@PathVariable("token") long tokennumber) {
        Metadata metadata = metadataRepository.findByTokennumber(tokennumber).orElseThrow(() -> new ExceptionHandling.NFTnotExistingException("This Token number does not exist"));

        String address = ethComponent.getCurrentOwner(tokennumber);
        EthOwnerResponse response = new EthOwnerResponse();

        if (address != null) {
            response.setTokenid(tokennumber);
            response.setSuccess(true);
            response.setAddress(address);
        } else
            response.setSuccess(false);

        return response;
    }

    public static class EthOwnerResponse {
        @Getter
        @Setter
        boolean success;

        @Getter
        @Setter
        long tokenid;

        @Getter
        @Setter
        String address;
    }

    @ResponseBody
    @GetMapping(value = "/gasprice", produces = "application/json")
    public Gasprice getGasprice() { //Gas price in wei
        Gasprice response = new Gasprice();
        long gasPriceInWei = ethComponent.getRecommendedGaspriceInGwei() * 1_000_000_000L;
        response.setGasPriceInWei(gasPriceInWei);
        return response;
    }

    public static class Gasprice {
        @Getter
        @Setter
        long gasPriceInWei;
    }

    @ResponseBody
    @GetMapping(value = "/mintingcost", produces = "application/json")
    public ApplicationData.Mintingcost mintingcost() {
        return applicationData.getMintingCost(ethComponent);
    }

}