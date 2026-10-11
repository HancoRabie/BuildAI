package com.hanco.buildai.client;

import com.google.gson.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Validate OIDC identity independently of the browser callback or token endpoint. */
final class ChatGPTIdentity {
    private ChatGPTIdentity() {}
    private static final String ISSUER="https://auth.openai.com";
    private static final URI DISCOVERY=URI.create(ISSUER+"/.well-known/openid-configuration");
    private static final HttpClient HTTP=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12)).build();

    static JsonObject validate(String jwt,String clientId,String nonce) throws Exception {
        String[] parts=jwt.split("\\.");
        if(parts.length!=3)throw new SecurityException("Missing signed account identity");
        JsonObject header=parseSegment(parts[0]);
        if(!"RS256".equals(field(header,"alg")))throw new SecurityException("Unsupported ID token signature");
        String kid=field(header,"kid");
        HttpResponse<String> metadata=HTTP.send(
            HttpRequest.newBuilder(DISCOVERY).timeout(Duration.ofSeconds(15)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if(metadata.statusCode()!=200)throw new SecurityException("OpenAI identity discovery failed");
        String keyUrl=field(JsonParser.parseString(metadata.body()).getAsJsonObject(),"jwks_uri");
        URI keys=URI.create(keyUrl);
        if(!"https".equals(keys.getScheme())||!"auth.openai.com".equalsIgnoreCase(keys.getHost()))
            throw new SecurityException("Unexpected OpenAI key source");
        HttpResponse<String> keyResponse=HTTP.send(
            HttpRequest.newBuilder(keys).timeout(Duration.ofSeconds(15)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if(keyResponse.statusCode()!=200)throw new SecurityException("Cannot verify ChatGPT identity");
        JsonArray available=JsonParser.parseString(keyResponse.body()).getAsJsonObject().getAsJsonArray("keys");
        boolean verified=false;
        for(JsonElement element:available) {
            JsonObject jwk=element.getAsJsonObject();
            if(!kid.equals(field(jwk,"kid"))||!"RSA".equals(field(jwk,"kty")))continue;
            BigInteger n=new BigInteger(1,Base64.getUrlDecoder().decode(field(jwk,"n")));
            BigInteger e=new BigInteger(1,Base64.getUrlDecoder().decode(field(jwk,"e")));
            RSAPublicKey pub=(RSAPublicKey)KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(n,e));
            Signature sig=Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);
            sig.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            verified=sig.verify(Base64.getUrlDecoder().decode(parts[2]));
            break;
        }
        if(!verified)throw new SecurityException("ChatGPT identity signature could not be verified");
        JsonObject claims=parseSegment(parts[1]);
        if(!ISSUER.equals(field(claims,"iss"))||!nonce.equals(field(claims,"nonce")))
            throw new SecurityException("OpenAI identity issuer or nonce mismatch");
        JsonElement aud=claims.get("aud");
        boolean audience=false;
        if(aud!=null){
            if(aud.isJsonPrimitive())audience=clientId.equals(aud.getAsString());
            else if(aud.isJsonArray())
                for(JsonElement item:aud.getAsJsonArray())if(clientId.equals(item.getAsString()))audience=true;
        }
        if(!audience)throw new SecurityException("Token was not issued to BuildAI");
        long now=Instant.now().getEpochSecond();
        if(!claims.has("exp")||claims.get("exp").getAsLong()<=now||
            (claims.has("iat")&&claims.get("iat").getAsLong()>now+120))
            throw new SecurityException("Account identity expired or clock is incorrect");
        if(field(claims,"sub").isBlank())throw new SecurityException("Missing account subject");
        return claims;
    }
    static String field(JsonObject object,String key){
        return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";
    }
    private static JsonObject parseSegment(String part){
        return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(part),
            StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
