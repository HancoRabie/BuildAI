package com.hanco.buildai.client;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only sideband health probe. Does NOT send the player's prompt,
 * alter/recreate the running response, send a nudge to the model, consume
 * generation tokens or read the active stream. GET /models proves at most
 * API reachability / authorization, not this response's running state.
 *
 * Model output was sent store:false, so there is no reliably retrievable
 * stored response to poll. Never present this as a remote task status API.
 */
final class BuildAIRequestHealth {
    private BuildAIRequestHealth(){}

    static void check(GenerationProgress progress){
        if(progress==null||!progress.beginCheck())return;
        final String existing=progress.localDiagnostic();
        CompletableFuture.supplyAsync(()->{
            try{
                String token=ChatGPTSignIn.connected()
                    ?ChatGPTSignIn.token():BuildAIClient.getKey();
                if(token==null||token.isBlank())
                    return "Connection check unavailable: no connected account/API key. "+
                        "Request state: "+existing;
                HttpRequest req=HttpRequest.newBuilder(
                    URI.create("https://api.openai.com/v1/models"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization","Bearer "+token)
                    .GET().build();
                HttpResponse<Void> result=ChatGPTNetwork.send(req,
                    HttpResponse.BodyHandlers.discarding(),
                    "checking API reachability, independently of the ongoing blueprint");
                if(result.statusCode()>=200&&result.statusCode()<300)
                    return "OpenAI reachable (HTTP "+result.statusCode()+
                        "). This ONLY checks your connection/access; "+
                        "it cannot confirm the original model is still reasoning. "+
                        existing;
                return "OpenAI probe returned HTTP "+result.statusCode()+
                    ". This check may have different permissions from the active "+
                    "model session and does NOT cancel or prove failure. "+
                    existing;
            }catch(Exception error){
                Throwable cause=error;
                while(cause.getCause()!=null)cause=cause.getCause();
                String type=cause.getClass().getSimpleName();
                return "Independent OpenAI health probe failed ("+type+
                    "). Active generation was NOT touched. "+existing;
            }
        }).whenComplete((result,error)->{
            String outcome=result;
            if(error!=null)outcome="Health probe unavailable; "+
                "the active request and saved prompt are unchanged. "+existing;
            progress.completeCheck(outcome);
        });
    }
}
