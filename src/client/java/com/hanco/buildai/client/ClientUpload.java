package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;
import com.hanco.buildai.net.BlueprintChunkPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Upload only validated geometry, never local files, credentials or AI conversation data. */
public final class ClientUpload {
    private ClientUpload(){}
    public static void send(Blueprint b,int surfaceLayer) throws IOException {
        BlockStates.validate(b);
        if(!ClientPlayNetworking.canSend(BlueprintChunkPayload.ID))
            throw new IOException("Server does not have BuildAI installed / connected");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(GZIPOutputStream gzip=new GZIPOutputStream(output)){
            gzip.write(BlueprintJson.encode(b).toString().getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes=output.toByteArray();
        int count=(bytes.length+16383)/16384;
        if(count>2048)throw new IOException("Blueprint data is too large for one safe upload; export in sections, do not reduce the architectural scale.");
        String id=UUID.randomUUID().toString();
        for(int i=0;i<count;i++){
            int from=i*16384,to=Math.min(bytes.length,from+16384);
            ClientPlayNetworking.send(new BlueprintChunkPayload(id,i,count,surfaceLayer,Arrays.copyOfRange(bytes,from,to)));
        }
    }
}
