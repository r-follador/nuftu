package com.genewarrior.nuftu;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class ExceptionHandling {


    @ExceptionHandler({MaxUploadSizeExceededException.class, SizeLimitExceededException.class, FileSizeLimitExceededException.class})
    public final String handleFileTooLarge(Exception ex, Model model) {
        //TODO: show some error message for too large files
        model.addAttribute("errormessage", "File too large.");
        model.addAttribute("errormessagedetail", "File too large. Max size is 20MB");

        return "error";
    }

    @ExceptionHandler({FileNotAccepted.class})
    public final ResponseEntity<Object> handleWrongFileException(FileNotAccepted ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setResponse(ex.msg);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({EthException.class})
    public final String handleEthException(EthException ex, Model model) {
        model.addAttribute("errormessage", ex.msg);
        model.addAttribute("errormessagedetail", ex.additionalMsg);

        return "error";
    }

    @ExceptionHandler({NFTnotExistingException.class})
    public final String handleNFTnotExistingException(NFTnotExistingException ex, Model model) {
        model.addAttribute("errormessage", ex.msg);
        return "error";
    }


    @AllArgsConstructor
    public static class FileNotAccepted extends RuntimeException {
        public String msg = "";
    }

    @AllArgsConstructor
    public static class EthException extends RuntimeException {
        public String msg = "";
        public String additionalMsg = "";
    }

    @AllArgsConstructor
    public static class NFTnotExistingException extends RuntimeException {
        public String msg = "";
    }

    public static class ErrorResponse {
        @Getter
        @Setter
        boolean error = true;

        @Getter
        @Setter
        String type;

        @Getter
        @Setter
        String response;
    }

}
