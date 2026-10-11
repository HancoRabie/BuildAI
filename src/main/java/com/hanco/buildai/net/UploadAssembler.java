package com.hanco.buildai.net;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;

/** Per-player bounded upload buffers; rejects malformed duplicate and excess packets. */
public final class UploadAssembler {
    private static final int MAX_CHUNKS=2048,MAX_COMPRESSED=33_600_000,MAX_PLAIN=80_000_000;
    private final Map<UUID,Pending> pending=new HashMap<>();
    private record Pending(String id,byte[][] chunks,int surface,long created) {}
    public synchronized String accept(UUID user,BlueprintChunkPayload p) throws IOException {
        if(p.count()<1||p.count()>MAX_CHUNKS||p.index()<0||p.index()>=p.count()||p.bytes().length>20480
            ||p.surfaceLayer()<0||p.surfaceLayer()>511)throw new IOException("Invalid upload packet");
        Pending q=pending.get(user);
        if(q==null||!q.id.equals(p.uploadId())||System.currentTimeMillis()-q.created>120000L) {
            q=new Pending(p.uploadId(),new byte[p.count()][],p.surfaceLayer(),System.currentTimeMillis());
            pending.put(user,q);
        }
        if(q.chunks.length!=p.count()||q.surface!=p.surfaceLayer())throw new IOException("Upload protocol mismatch");
        if(q.chunks[p.index()]!=null)throw new IOException("Duplicate upload chunk");
        q.chunks[p.index()]=p.bytes();
        int total=0;
        for(byte[] part:q.chunks){
            if(part==null)return null;
            total+=part.length;
            if(total>MAX_COMPRESSED)throw new IOException("Uploaded file too large");
        }
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        for(byte[] part:q.chunks)buffer.write(part);
        pending.remove(user);
        try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            ByteArrayOutputStream decoded=new ByteArrayOutputStream();
            byte[] tmp=new byte[8192];int n;
            while((n=gzip.read(tmp))!=-1){
                if(decoded.size()+n>MAX_PLAIN)throw new IOException("Decompressed blueprint exceeds limit");
                decoded.write(tmp,0,n);
            }
            return q.surface+"\n"+decoded.toString(StandardCharsets.UTF_8);
        }
    }
}
