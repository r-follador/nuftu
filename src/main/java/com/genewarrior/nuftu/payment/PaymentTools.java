package com.genewarrior.nuftu.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;


//See: https://developers.payrexx.com/reference#rest-api


public class PaymentTools {

    private static String apiKey = "xxx";
    private static String instance = "nuftu";
    private static String apiUrl = "https://api.payrexx.com/v1.0/Gateway/";

    private static String getApiSignature(HashMap<String, String> postData) {
        String payload = getPayload(postData);
        return getApiSignature(payload);
    }

    private static HashMap<String, String> addApiSignature(HashMap<String, String> postData) {
        if (postData.containsKey("ApiSignature"))
            throw new RuntimeException("Cannot add ApiSignature: HashMap already contains key 'ApiSignature'");
        postData.put("ApiSignature", PaymentTools.getApiSignature(postData));
        return postData;
    }

    public static PayrexxGateway createGateway(PayrexxGateway payrexxGateway, OkHttpClient client) throws IOException, PaymentToolException {
        HashMap<String, String> postdata = addApiSignature(payrexxGateway.getPostdata());

        Request request = new Request.Builder()
                .url(apiUrl + "?instance=" + instance)
                .post(PaymentTools.makeFormBody(postdata))
                .build();

        JsonNode jsonNode = callProvider(client, request);

        int paymentId = jsonNode.path("data").get(0).path("id").asInt(-1);
        String paymentUrl = jsonNode.path("data").get(0).path("link").asText("not found");
        String paymentStatus = jsonNode.path("data").get(0).path("status").asText("not found");

        payrexxGateway.setRetrievedData(paymentId, paymentUrl, paymentStatus);

        return payrexxGateway;
    }

    public static PayrexxGateway refreshGateway(PayrexxGateway payrexxGateway, OkHttpClient client) throws IOException, PaymentToolException {

        HttpUrl.Builder httpBuilder = HttpUrl.parse(apiUrl + payrexxGateway.getPaymentId() + "/?instance=" + instance).newBuilder();
        httpBuilder.addQueryParameter("ApiSignature", getEmptyApiSignature());
        Request request = new Request.Builder().url(httpBuilder.build()).build();

        JsonNode jsonNode = callProvider(client, request);
        String paymentStatus = jsonNode.path("data").get(0).path("status").asText("blarg");

        payrexxGateway.updateStatus(paymentStatus);

        return payrexxGateway;
    }

    public static PayrexxGateway.PaymentStatus getGatewayStatus(int paymentId, OkHttpClient client) throws IOException, PaymentToolException {

        HttpUrl.Builder httpBuilder = HttpUrl.parse(apiUrl + paymentId + "/?instance=" + instance).newBuilder();
        httpBuilder.addQueryParameter("ApiSignature", getEmptyApiSignature());
        Request request = new Request.Builder().url(httpBuilder.build()).build();

        JsonNode jsonNode = callProvider(client, request);
        String paymentStatus = jsonNode.path("data").get(0).path("status").asText("blarg");
        return PayrexxGateway.PaymentStatus.valueOf(paymentStatus);
    }

    public static String getPaymentUrlFromId(int paymentId, OkHttpClient client) throws PaymentToolException, IOException {
        HttpUrl.Builder httpBuilder = HttpUrl.parse(apiUrl + paymentId + "/?instance=" + instance).newBuilder();
        httpBuilder.addQueryParameter("ApiSignature", getEmptyApiSignature());
        Request request = new Request.Builder().url(httpBuilder.build()).build();

        JsonNode jsonNode = callProvider(client, request);
        String paymentLink = jsonNode.path("data").get(0).path("link").asText("blarg");
        if (paymentLink.equals("blarg"))
            throw new PaymentToolException("Error getting Payment link from id: " + paymentId + "; malformatted Json: " + jsonNode.toString());
        return paymentLink;
    }

    public static PayrexxGateway deleteGateway(PayrexxGateway payrexxGateway, OkHttpClient client) throws IOException, PaymentToolException {
        HttpUrl.Builder httpBuilder = HttpUrl.parse(apiUrl + payrexxGateway.getPaymentId() + "/?instance=" + instance).newBuilder();
        httpBuilder.addQueryParameter("ApiSignature", getEmptyApiSignature());
        Request request = new Request.Builder().url(httpBuilder.build()).delete().build();

        callProvider(client, request);

        payrexxGateway.deleteGateway();

        return payrexxGateway;
    }

    private static JsonNode callProvider(OkHttpClient client, Request request) throws IOException, PaymentToolException {
        Call call = client.newCall(request);
        Response response = call.execute();
        if (response.code() != 200) {
            throw new PaymentToolException("Error calling '" + request.url().toString() + "': Http Status is " + response.code());
        }

        JsonNode jsonNode = (new ObjectMapper()).readTree(response.body().string());

        String status = jsonNode.path("status").asText();
        if (!status.equalsIgnoreCase("success")) {
            throw new PaymentToolException("Error calling '" + request.url().toString() + "': Json response status is not success: " + status);
        }

        if (jsonNode.path("data").get(0).path("id").asInt(-1) < 0) {
            throw new PaymentToolException("Error parsing JSON from '" + request.url().toString() + "': " + jsonNode.toString());
        }

        return jsonNode;
    }


    private static RequestBody makeFormBody(final HashMap<String, String> map) {
        FormBody.Builder formBody = new FormBody.Builder();
        for (final HashMap.Entry<String, String> entrySet : map.entrySet()) {
            formBody.add(entrySet.getKey(), entrySet.getValue());
        }
        return formBody.build();
    }


    private static String getEmptyApiSignature() {
        return getApiSignature("");
    }

    private static String getApiSignature(String encodedData) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(encodedData.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static String getPayload(HashMap<String, String> postData) {
        AtomicReference<String> urlEncoded = new AtomicReference<>("");
        postData.forEach((s, s2) -> {
            String concat = urlEncoded.get().isEmpty() ? "" : "&";
            urlEncoded.set(urlEncoded.get() + concat + s + "=" + URLEncoder.encode(s2, StandardCharsets.UTF_8));
        });

        return urlEncoded.get();
    }

    public static class PaymentToolException extends Exception {
        public PaymentToolException(String message) {
            super(message);
        }
    }
}
