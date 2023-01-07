package com.genewarrior.nuftu.controllers;

import com.genewarrior.nuftu.EthComponent;
import lombok.Getter;
import lombok.Setter;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.Canvas;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web3j.crypto.WalletUtils;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class UploadAsset {
    @Autowired
    EthComponent ethComponent;

    Logger logger = LoggerFactory.getLogger(UploadAsset.class);

    @Getter
    @Setter
    @NotNull
    @Size(min = 1, max = 255, message = "Size of the title must be between 1 and 255 characters")
    public String name;

    @Getter
    @Setter
    @NotNull
    @Size(min = 1, max = 1000, message = "Size of the title must be between 1 and 1000 characters")
    public String description;

    @Getter
    @Setter
    @Email(message = "Email not valid")
    public String email;

    @Getter
    @Setter
    public MultipartFile file;

    @Getter
    @Setter
    public String address;

    @Getter
    boolean isChecked = false;

    @Getter
    boolean hasFileError = false;

    @Getter
    boolean hasAddressError = false;

    @Getter
    byte[] thumbnail;


    @Getter
    String fileErrorMessage;

    @Getter
    String addressErrorMessage;

    @Getter
    String mimetype;

    @Getter
    File assetFile;

    @Getter
    String sha256;

    public void check() {
        isChecked = true;

        if (getAddress() != null && !getAddress().isEmpty() && !WalletUtils.isValidAddress(getAddress())) {
            hasAddressError = true;
            addressErrorMessage = "The Ethereum Address you have entered is not valid.";
        }

        if (getFile().isEmpty()) {
            hasFileError = true;
            fileErrorMessage = "File is empty";
            return;
        }

        if (getFile().getSize() > 20_000_000) {
            hasFileError = true;
            fileErrorMessage = "File is too large; limit is 20 MB";
            logger.info("Upload failed: File too large: " + getFile().getSize() + " bytes");
            return;
        }

        try {
            Tika tika = new Tika();
            mimetype = tika.detect(this.getFile().getInputStream());
        } catch (IOException e) {
            hasFileError = true;
            fileErrorMessage = "File is corrupt";
            return;
        }

        switch (getMimetype()) {
            case "image/gif":
            case "image/jpeg":
            case "image/png":
            case "image/svg":
            case "image/svg+xml":
            case "video/mp4":
            case "video/webm":
            case "video/ogg":
            case "video/quicktime":
            case "audio/mpeg":
            case "audio/ogg":
            case "audio/wav":
                break;

            default:
                hasFileError = true;
                fileErrorMessage = "File type is not supported (" + getMimetype() + ")";
                logger.info("Upload failed: File not supported: " + getMimetype());
                return;
        }

        try {
            assetFile = File.createTempFile("nuftu_upload_", ".tmp");
            file.transferTo(assetFile);
            assetFile.deleteOnExit();
            sha256 = getFileChecksum(assetFile);

            if (getMimetype().startsWith("image/") && !getMimetype().startsWith("image/svg")) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                Thumbnails.of(assetFile).size(350, 350).addFilter(new Canvas(350, 350, Positions.CENTER, Color.WHITE)).outputFormat("jpg").toOutputStream(bos);
                thumbnail = bos.toByteArray();
            } else if (getMimetype().startsWith("audio/")) {
                thumbnail = ResourceUtils.getURL("classpath:static/assets/img/nuftu_audio.png").openStream().readAllBytes(); //https://stackoverflow.com/questions/35726500/using-resourceutils-getfile-to-read-a-file-from-classpath-in-heroku-environment
            }
        } catch (IOException e) {
            hasFileError = true;
            fileErrorMessage = "Cannot read file";
            logger.warn("Cannot read file", e);
            return;
        } catch (NoSuchAlgorithmException e) {
            //Ignore
        }


    }

    private static String getFileChecksum(File file) throws IOException, NoSuchAlgorithmException {
        //Get file input stream for reading the file content
        FileInputStream fis = new FileInputStream(file);

        //Create byte array to read data in chunks
        byte[] byteArray = new byte[1024];
        int bytesCount = 0;

        MessageDigest shaDigest = MessageDigest.getInstance("SHA-256");

        //Read file data and update in message digest
        while ((bytesCount = fis.read(byteArray)) != -1) {
            shaDigest.update(byteArray, 0, bytesCount);
        }
        ;

        //close the stream; We don't need it now.
        fis.close();

        //Get the hash's bytes
        byte[] bytes = shaDigest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString();
    }

}
