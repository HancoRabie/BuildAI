package com.hanco.buildai.client;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Open-source Sign in with ChatGPT; all tokens stay on the local Minecraft CLIENT. */
public final class ChatGPTSignIn {
    private ChatGPTSignIn(){}
    private static final String AUTHORIZE="https://auth.openai.com/api/accounts/authorize";
    private static final URI TOKEN=URI.create("https://auth.openai.com/api/accounts/oauth/token");
    private static final String RESOURCE="https://api.openai.com/v1";
    private static final String SCOPES="openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final Path DIR=Path.of(System.getProperty("user.home"),".buildai");
    private static final Path HOST=DIR.resolve("host-id.txt"), SESSION=DIR.resolve("chatgpt-account.json");
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private static final SecureRandom SECURE=new SecureRandom();
    private static volatile Account account;
    private static volatile boolean loaded=false;
    private static volatile boolean signingIn=false;
    private static volatile CompletableFuture<String> inFlightSignIn;
    private static volatile String pendingSignInUrl="";
    private static volatile String browserStatus="Preparing secure authorization...";

    /** This link is temporary and only works while the localhost callback is listening. */
    public static String pendingSignInUrl(){return pendingSignInUrl;}
    public static String browserStatus(){return browserStatus;}
    public static boolean signingIn(){return signingIn;}

    private record Account(String clientId,String subject,String email,String idToken,String access,
                           String refresh,String scopes,long expires,String hostId) {
        boolean canGenerate(){return Arrays.asList(scopes.split("\\s+")).contains("chatgpt.tokens.use.direct");}
        JsonObject toJson(){
            JsonObject o=new JsonObject();
            o.addProperty("client_id",clientId);o.addProperty("sub",subject);o.addProperty("email",email);
            o.addProperty("id_token",idToken);o.addProperty("access_token",access);
            o.addProperty("refresh_token",refresh);o.addProperty("scope",scopes);
            o.addProperty("expires_at",expires);o.addProperty("host_id",hostId);
            return o;
        }
        static Account parse(JsonObject o){
            return new Account(field(o,"client_id"),field(o,"sub"),field(o,"email"),field(o,"id_token"),
                field(o,"access_token"),field(o,"refresh_token"),field(o,"scope"),
                o.get("expires_at").getAsLong(),field(o,"host_id"));
        }
    }
    private static String field(JsonObject o,String name) {
        return o.has(name)&&!o.get(name).isJsonNull()?o.get(name).getAsString():"";
    }
    private static synchronized Account current(){
        if(!loaded){
            loaded=true;
            try{
                if(Files.isRegularFile(SESSION))account=Account.parse(
                    JsonParser.parseString(Files.readString(SESSION,StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(Exception ignored){account=null;}
        }
        return account;
    }
    public static boolean connected(){Account a=current();return a!=null&&a.canGenerate();}
    public static String accountLabel(){
        Account a=current();
        return a==null?"Not connected":a.email.isBlank()?"ChatGPT account connected":a.email;
    }
    public static synchronized void disconnect(){
        account=null;loaded=true;
        try{Files.deleteIfExists(SESSION);}catch(IOException ignored){}
    }
    private static String host() throws IOException {
        if(Files.isRegularFile(HOST)){
            String id=Files.readString(HOST,StandardCharsets.UTF_8).trim();
            if(id.startsWith("urn:uuid:"))return id;
        }
        String id="urn:uuid:"+UUID.randomUUID();
        safeWrite(HOST,id);
        return id;
    }
    private static void safeWrite(Path target,String content) throws IOException {
        Files.createDirectories(DIR);
        Path temp=Files.createTempFile(DIR,".buildai-", ".tmp");
        try{
            Files.writeString(temp,content,StandardCharsets.UTF_8);
            try{
                Files.setPosixFilePermissions(temp,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
            }catch(UnsupportedOperationException e){
                AclFileAttributeView acl=Files.getFileAttributeView(temp,AclFileAttributeView.class);
                if(acl==null)throw new IOException("Cannot protect ChatGPT credentials on this filesystem");
                var owner=Files.getOwner(temp);
                var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(AclEntryPermission.values()).build();
                acl.setAcl(List.of(entry));
            }
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static void save(Account a) throws IOException {
        safeWrite(SESSION,a.toJson().toString());
    }
    private static String random(int n){
        byte[] data=new byte[n];SECURE.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
    private static String codeChallenge(String verifier){
        try{
            byte[] sum=MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sum);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static String form(Map<String,String> fields){
        StringJoiner j=new StringJoiner("&");
        fields.forEach((k,v)->j.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
        return j.toString();
    }
    private static Map<String,String> query(String q){
        Map<String,String> parsed=new HashMap<>();
        if(q==null)return parsed;
        for(String pair:q.split("&",32)){
            String[] values=pair.split("=",2);
            parsed.put(URLDecoder.decode(values[0],StandardCharsets.UTF_8),
                values.length>1?URLDecoder.decode(values[1],StandardCharsets.UTF_8):"");
        }
        return parsed;
    }
    private static JsonObject tokenRequest(Map<String,String> params) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(TOKEN).timeout(Duration.ofSeconds(30))
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(params),StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IOException("OpenAI authorization HTTP "+response.statusCode()+
            "; reconnect if the token expired or consent was declined");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static synchronized CompletableFuture<String> connect(){
        // Reopening F8 while a browser is already authorizing must REJOIN the same
        // localhost callback, never invalidate the link or start a competing login.
        if(signingIn && inFlightSignIn!=null)return inFlightSignIn;
        signingIn=true;
        pendingSignInUrl="";
        browserStatus="Starting secure local callback...";
        inFlightSignIn=CompletableFuture.supplyAsync(()->{
            HttpServer server=null;
            try{
                Account previous=current();
                String hostId=host();
                String state=random(32),nonce=random(32),verifier=random(64);
                server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);
                String redirect="http://127.0.0.1:"+server.getAddress().getPort()+"/auth/callback";
                CompletableFuture<Map<String,String>> callback=new CompletableFuture<>();
                server.createContext("/auth/callback",exchange->{
                    Map<String,String> values=query(exchange.getRequestURI().getRawQuery());
                    boolean valid=state.equals(values.get("state"));
                    byte[] body=(valid?"BuildAI received your authorization. Return to Minecraft.":
                        "BuildAI rejected an invalid authorization.").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control","no-store");
                    exchange.sendResponseHeaders(valid?200:400,body.length);
                    try(var out=exchange.getResponseBody()){out.write(body);}
                    if(valid)callback.complete(values);
                });
                server.start();
                Map<String,String> p=new LinkedHashMap<>();
                p.put("client_id",previous==null?"dynamic_agent_client":previous.clientId);
                if(previous==null)p.put("agent_name_hint","BuildAI");
                else if(!previous.idToken.isBlank())p.put("id_token_hint",previous.idToken);
                p.put("ext_agent_host_id",hostId);p.put("response_type","code");
                p.put("redirect_uri",redirect);p.put("scope",SCOPES);p.put("resource",RESOURCE);
                p.put("state",state);p.put("nonce",nonce);p.put("code_challenge_method","S256");
                p.put("code_challenge",codeChallenge(verifier));
                // Preserve the callback and PKCE verifier even when Minecraft's
                // bundled Java reports Desktop.browse() unsupported (common on Windows).
                // The user may copy the live authorization link in the in-game modal.
                pendingSignInUrl=AUTHORIZE+"?"+form(p);
                browserStatus="Opening your default browser...";
                boolean opened=BrowserLauncher.openOpenAiAuthorization(pendingSignInUrl);
                browserStatus=opened
                    ? "Browser launched. Approve BuildAI there, then return to Minecraft."
                    : "Browser could not open automatically. Click Copy Sign-in Link below.";
                Map<String,String> result=callback.get(300,TimeUnit.SECONDS);
                if(!state.equals(result.get("state")))throw new SecurityException("OAuth callback state mismatch");
                if(result.containsKey("error"))throw new IOException("Authorization denied: "+result.get("error"));
                String clientId=previous==null?result.get("client_id"):previous.clientId;
                if(clientId==null||clientId.isBlank()||clientId.equals("dynamic_agent_client"))
                    throw new SecurityException("OpenAI did not issue a valid client registration");
                if(previous!=null&&result.containsKey("client_id")&&!clientId.equals(result.get("client_id")))
                    throw new SecurityException("Unexpected OAuth client ID");
                String code=result.get("code");
                if(code==null||code.isBlank())throw new IOException("Missing sign-in authorization code");
                JsonObject response=tokenRequest(Map.of("grant_type","authorization_code",
                    "client_id",clientId,"code",code,"code_verifier",verifier,
                    "redirect_uri",redirect,"resource",RESOURCE));
                String idToken=field(response,"id_token");
                JsonObject identity=ChatGPTIdentity.validate(idToken,clientId,nonce);
                String subject=field(identity,"sub");
                if(previous!=null&&!previous.subject.equals(subject))
                    throw new SecurityException("A different ChatGPT account was selected");
                String scopes=field(response,"scope");
                Account connected=new Account(clientId,subject,field(identity,"email"),idToken,
                    field(response,"access_token"),field(response,"refresh_token"),scopes,
                    Instant.now().getEpochSecond()+response.get("expires_in").getAsLong(),hostId);
                if(!connected.canGenerate())throw new IOException("ChatGPT plan usage was not authorized for BuildAI");
                if(connected.access.isBlank())throw new IOException("OpenAI returned no inference token");
                save(connected);account=connected;loaded=true;
                return "ChatGPT connected: "+accountLabel();
            }catch(Exception e){throw new CompletionException(e);}
            finally{
                if(server!=null)server.stop(0);
                pendingSignInUrl="";
                browserStatus="Sign-in session ended.";
                synchronized(ChatGPTSignIn.class){signingIn=false;}
            }
        });
        return inFlightSignIn;
    }
    /** Serialized refresh; never use a revoked or expired access token. */
    public static synchronized String token() throws Exception {
        Account a=current();
        if(a==null||!a.canGenerate())throw new IOException("Connect ChatGPT in BuildAI F8 first");
        if(a.expires>Instant.now().getEpochSecond()+90)return a.access;
        if(a.refresh.isBlank())throw new IOException("ChatGPT sign-in expired: reconnect via F8");
        JsonObject refreshed=tokenRequest(Map.of("grant_type","refresh_token","client_id",a.clientId,
            "refresh_token",a.refresh,"resource",RESOURCE));
        String scopes=field(refreshed,"scope");
        if(scopes.isBlank())scopes=a.scopes;
        Account next=new Account(a.clientId,a.subject,a.email,a.idToken,field(refreshed,"access_token"),
            refreshed.has("refresh_token")?field(refreshed,"refresh_token"):a.refresh,
            scopes,Instant.now().getEpochSecond()+refreshed.get("expires_in").getAsLong(),a.hostId);
        if(!next.canGenerate()||next.access.isBlank())
            throw new IOException("ChatGPT sharing permission expired or was revoked");
        save(next);account=next;return next.access;
    }
    /** Choose only a model OpenAI says this account can actually use. */
    public static String model() throws Exception {
        String bearer=token();
        HttpRequest req=HttpRequest.newBuilder(URI.create(RESOURCE+"/models"))
            .timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+bearer).GET().build();
        HttpResponse<String> res=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=200)throw new IOException("ChatGPT model list HTTP "+res.statusCode());
        JsonObject catalog=JsonParser.parseString(res.body()).getAsJsonObject();
        JsonArray models=catalog.getAsJsonArray("models");
        if(models==null||models.isEmpty())throw new IOException("No supported ChatGPT plan models available");
        List<String> slugs=new ArrayList<>();
        for(JsonElement e:models){
            JsonObject entry=e.getAsJsonObject();
            if(!"list".equals(field(entry,"visibility")))continue;
            String slug=field(entry,"slug");
            if(!slug.isBlank())slugs.add(slug);
        }
        if(slugs.isEmpty())throw new IOException("No available ChatGPT generation models for this account");
        // Prioritize commonly supported reasoning/code capable models; fallback follows official catalog order.
        for(String preferred:List.of("gpt-6.1-sol","gpt-6","gpt-5.6","gpt-5.5"))
            if(slugs.contains(preferred))return preferred;
        return slugs.get(0);
    }
}
