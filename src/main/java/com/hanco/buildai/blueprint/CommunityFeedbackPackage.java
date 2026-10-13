package com.hanco.buildai.blueprint;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * Player-consented, LOCAL EXPORT of useful BuildAI training/evaluation
 * examples. NO HTTP/upload/network methods. The player independently opens
 * GitHub and attaches the generated ZIP after reviewing its contents.
 *
 * Public exports never contain the original raw .schem's embedded prompts,
 * AI design history, command scripts, arbitrary block entity NBT, world
 * coordinates, inventory data, absolute file paths or sign text.
 */
public final class CommunityFeedbackPackage {
    private CommunityFeedbackPackage(){}
    public static final String SUBMISSION_URL=
        "https://github.com/HancoRabie/BuildAI/issues/new?template=redstone-feedback.md";
    public static final String LICENSE="CC BY 4.0";
    private static final long MAX_ZIP_BYTES=23_000_000L;

    public record Report(String pillar,String verification,int rating,
                         String observed,String expected,String explanation,
                         boolean rightsConfirmed){
        public Report {
            if(!rightsConfirmed)throw new IllegalArgumentException(
                "Explicit permission to share the contribution is required.");
            if(pillar==null||!List.of("Power & transmission","Boolean logic",
                "Pulse engineering","Clocks & timing","Memory & sequential",
                "Analog & sensors","Pistons & block updates",
                "Items & automation","Other").contains(pillar))
                throw new IllegalArgumentException("Choose a valid redstone pillar");
            if(verification==null||!List.of("Failure observed","Correction unverified",
                "Corrected and tested").contains(verification))
                throw new IllegalArgumentException("Choose a verification status");
            if(rating<1||rating>5)throw new IllegalArgumentException("Rating must be 1-5");
            observed=clean(observed,1100);
            expected=clean(expected,1100);
            explanation=clean(explanation,2200);
            if(observed.isBlank()||expected.isBlank())
                throw new IllegalArgumentException(
                    "Provide both EXPECTED and OBSERVED behavior for a useful example.");
        }
    }
    private static String clean(String s,int limit){
        if(s==null)return "";
        String val=s.strip().replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"," ");
        if(val.length()>limit)throw new IllegalArgumentException("Feedback exceeds "+limit+" characters");
        return val;
    }

    /** Pure geometry clone. No source identity, prompts, scripts or sign text. */
    public static Blueprint anonymizedGeometry(Blueprint original){
        Objects.requireNonNull(original);
        Blueprint b=new Blueprint("community_submission",original.width,
            original.height,original.length);
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)
            for(int x=0;x<b.width;x++)
                b.set(x,y,z,original.get(x,y,z));
        // No .setGenerationContext, .setCommands, .setSigns.
        return b;
    }

    private static String sha(byte[] bytes){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void zip(ZipOutputStream out,String name,byte[] bytes)
        throws IOException{
        ZipEntry entry=new ZipEntry(name);
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }

    public static Path packageBytes(Path folder,Report feedback,byte[] generated,
                                    byte[] correction) throws IOException {
        Objects.requireNonNull(folder);
        Objects.requireNonNull(feedback);
        if(generated==null||generated.length==0)
            throw new IOException("Original anonymized schematic is required");
        if(generated.length>MAX_ZIP_BYTES || (correction!=null&&correction.length>MAX_ZIP_BYTES))
            throw new IOException("Schematic too large for a GitHub attachment");
        JsonObject report=new JsonObject();
        report.addProperty("schema","buildai-community-example-v1");
        report.addProperty("minecraft","Java 1.21.1");
        report.addProperty("pillar",feedback.pillar());
        report.addProperty("verification",feedback.verification());
        report.addProperty("rating",feedback.rating());
        report.addProperty("expected",feedback.expected());
        report.addProperty("observed",feedback.observed());
        report.addProperty("explanation",feedback.explanation());
        report.addProperty("generated_sha256",sha(generated));
        if(correction!=null)
            report.addProperty("corrected_sha256",sha(correction));
        report.addProperty("license",LICENSE);
        report.addProperty("rights_confirmed",true);
        report.addProperty("contains_verified_solution",
            correction!=null&&feedback.verification().equals("Corrected and tested"));
        String readme="""
            BuildAI community redstone contribution, schema v1

            Contents:
            - report.json: expected vs observed behavior, pillar and test status
            - generated.schem: sanitized geometry from the AI-generated design
            - corrected.schem (optional): sanitized geometry from player's fix

            The ZIP contains no authentication details, original AI prompt,
            embedded model history, executable command instructions, sign text,
            player/world identity or local file paths. Review before posting.

            IMPORTANT: "Failure observed" is NOT a verified working design.
            "Correction unverified" is NOT a validated solution.
            Even "Corrected and tested" is contributor-reported until independently
            reproduced in Minecraft Java 1.21.1. No code executes on import.

            Permission: contributor confirms original rights to publish and
            release these materials under Creative Commons Attribution 4.0.
            https://creativecommons.org/licenses/by/4.0/

            Submit by opening BuildAI's GitHub redstone-feedback issue form,
            attaching this ZIP to the issue body and posting the issue.
            This is a PUBLIC submission linked to your GitHub account.
            BuildAI does not upload the ZIP on its own.
            """;
        Files.createDirectories(folder);
        String stem="redstone-feedback-"+
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))+"-"+
            UUID.randomUUID().toString().substring(0,8);
        Path result=folder.resolve(stem+".zip");
        try(ZipOutputStream out=new ZipOutputStream(
            Files.newOutputStream(result,StandardOpenOption.CREATE_NEW),
            StandardCharsets.UTF_8)){
            zip(out,"report.json",report.toString().getBytes(StandardCharsets.UTF_8));
            zip(out,"README.txt",readme.getBytes(StandardCharsets.UTF_8));
            zip(out,"generated.schem",generated);
            if(correction!=null)zip(out,"corrected.schem",correction);
        }catch(Exception failure){
            Files.deleteIfExists(result);
            throw failure;
        }
        if(Files.size(result)>MAX_ZIP_BYTES){
            Files.deleteIfExists(result);
            throw new IOException("ZIP exceeds 23 MB safe attachment limit");
        }
        return result;
    }

    /** Only invoked by a user choosing PREPARE after the rights checkbox. */
    public static Path prepare(Path initial,Path fixed,Path outputDir,
                               Report report) throws IOException {
        if(!Files.isRegularFile(initial))
            throw new IOException("The selected generated schematic is missing");
        if(fixed!=null&&!Files.isRegularFile(fixed))
            throw new IOException("The selected corrected schematic is missing");
        if(report.verification().equals("Corrected and tested")&&fixed==null)
            throw new IOException("Verified correction requires a corrected schematic");
        Path a=null,b=null;
        try{
            Files.createDirectories(outputDir);
            a=Files.createTempFile(outputDir,".buildai-clean-original-",".schem");
            Blueprint original=anonymizedGeometry(SchematicReader.read(initial));
            SchematicWriter.write(original,a);
            if(fixed!=null){
                b=Files.createTempFile(outputDir,".buildai-clean-corrected-",".schem");
                Blueprint corrected=anonymizedGeometry(SchematicReader.read(fixed));
                SchematicWriter.write(corrected,b);
            }
            return packageBytes(outputDir,report,Files.readAllBytes(a),
                b==null?null:Files.readAllBytes(b));
        }finally{
            if(a!=null)Files.deleteIfExists(a);
            if(b!=null)Files.deleteIfExists(b);
        }
    }
}
