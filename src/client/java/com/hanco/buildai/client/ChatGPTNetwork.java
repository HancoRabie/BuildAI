package com.hanco.buildai.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * HTTPS-only communication and safe, stage-specific diagnostics for delegated
 * ChatGPT authorization. Never include the OAuth code, access token, query
 * string, request body or response body in exception messages.
 */
final class ChatGPTNetwork {
    private ChatGPTNetwork() {}

    static HttpClient newClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    record TextReply(int status, String body) {}

    static TextReply exchangeCode(URI endpoint, String formBody) throws IOException, InterruptedException {
        requireOfficial(endpoint);
        HttpRequest request=HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody,StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response=newClient().send(request,HttpResponse.BodyHandlers.ofString());
            return new TextReply(response.statusCode(),response.body());
        } catch(IOException | RuntimeException primary) {
            if(!isResolutionError(primary))
                throw diagnostic("exchanging the sign-in code",endpoint,primary);
            // A name-resolution failure means no authorization code was delivered.
            // Try the independent JDK URLConnection HTTPS stack. Never retry on
            // timeouts/HTTP failures because OAuth authorization codes are single-use.
            try {
                return exchangeViaURLConnection(endpoint,formBody);
            } catch(IOException | RuntimeException fallback) {
                IOException detail=diagnostic("exchanging the sign-in code",endpoint,fallback);
                detail.addSuppressed(diagnostic("first Java HTTP attempt",endpoint,primary));
                throw detail;
            }
        }
    }

    private static TextReply exchangeViaURLConnection(URI endpoint, String formBody) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)endpoint.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        connection.setDoOutput(true);
        try {
            try(var out=connection.getOutputStream()) {
                out.write(formBody.getBytes(StandardCharsets.UTF_8));
            }
            int status=connection.getResponseCode();
            InputStream response=status>=400?connection.getErrorStream():connection.getInputStream();
            String body;
            try(var stream=response) {
                body=stream==null?"":new String(stream.readNBytes(65536),StandardCharsets.UTF_8);
            }
            return new TextReply(status,body);
        } finally {
            connection.disconnect();
        }
    }

    static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                    String phase) throws IOException, InterruptedException {
        requireOfficial(request.uri());
        try {
            return newClient().send(request,handler);
        }catch(IOException | RuntimeException ex){
            throw diagnostic(phase,request.uri(),ex);
        }
    }

    private static void requireOfficial(URI uri) {
        if(!"https".equalsIgnoreCase(uri.getScheme()) ||
            !("auth.openai.com".equalsIgnoreCase(uri.getHost()) ||
              "api.openai.com".equalsIgnoreCase(uri.getHost())))
            throw new SecurityException("Only official OpenAI HTTPS endpoints are allowed");
    }

    private static boolean isResolutionError(Throwable ex) {
        while(ex!=null) {
            if(ex instanceof UnresolvedAddressException || ex instanceof UnknownHostException)return true;
            ex=ex.getCause();
        }
        return false;
    }

    private static IOException diagnostic(String phase,URI endpoint,Throwable failure) {
        String host=endpoint.getHost();
        String type=isResolutionError(failure)?"DNS/address resolution":
            failure instanceof java.net.SocketTimeoutException?"connection timeout":
            failure instanceof javax.net.ssl.SSLException?"TLS verification":
            "network connection";
        String action=isResolutionError(failure)
            ? "Java could not resolve this host or its configured proxy. Brave can use different DNS/proxy settings. "+
              "In Windows PowerShell run Resolve-DnsName "+host+"; inspect VPN/proxy/DNS settings."
            : "Check Windows connectivity, firewall and proxy configuration before retrying.";
        return new IOException("ChatGPT "+type+" failed while "+phase+" ("+host+"). "+action,failure);
    }
}
