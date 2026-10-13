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
    public static final String PREFERENCE_SUBMISSION_URL=
        "https://github.com/HancoRabie/BuildAI/issues/new?template=build-preference.md";
    public static final String SCHEMA="buildai-community-example-v2";
    public static final int SCHEMA_VERSION=2;
    public static final String REDSTONE="redstone_insight";
    public static final String PREFERENCE="build_preference";
    public static final String LICENSE="CC BY 4.0";
    private static final long MAX_ZIP_BYTES=23_000_000L;
    private static final Set<String> PILLARS=Set.of(
        "Power & transmission","Boolean logic","Pulse engineering",
        "Clocks & timing","Memory & sequential","Analog & sensors",
        "Pistons & block updates","Items & automation","Other");
    private static final Set<String> CLAIMS=Set.of(
        "Failure observed","Correction unverified","Corrected and tested");

    public static String submissionUrl(String category){
        return PREFERENCE.equals(category)?PREFERENCE_SUBMISSION_URL:SUBMISSION_URL;
    }
    /** Versions describe the EXPORTER runtime, not an unknown original model
     * or source schematic version. Benchmark ID and revision are user-supplied
     * and never used as independent proof of functionality. */
    public record Versions(String exporterBuildAi,String minecraft,String fabricLoader,
                           String benchmarkId,String benchmarkRevision,
                           String exporterSourceRevision) {
        public Versions {
            exporterBuildAi=version(exporterBuildAi,60);
            minecraft=version(minecraft,40);
            fabricLoader=version(fabricLoader,40);
            benchmarkId=version(benchmarkId,64);
            benchmarkRevision=version(benchmarkRevision,48);
            exporterSourceRevision=version(exporterSourceRevision,40);
        }
        // Schema compatibility for older tests/call sites, but unknown
        // source revisions stay explicit instead of being fabricated.
        public Versions(String buildAi,String minecraft,String loader,
                        String benchmarkId,String benchmarkRevision){
            this(buildAi,minecraft,loader,benchmarkId,benchmarkRevision,"unknown");
        }
        public static Versions unspecified(){
            return new Versions("unspecified","1.21.1","unspecified","","","unknown");
        }
    }
    private static String version(String s,int max){
        s=clean(s,max);
        return s.isBlank()?"unspecified":s;
    }

    public record Report(String category,String pillar,String verification,int rating,
                         String observed,String expected,String explanation,
                         Versions versions,boolean rightsConfirmed) {
        public Report {
            if(!rightsConfirmed)throw new IllegalArgumentException(
                "Explicit permission to share this public contribution is required.");
            if(!REDSTONE.equals(category)&&!PREFERENCE.equals(category))
                throw new IllegalArgumentException("Choose a supported feedback category");
            if(rating<1||rating>5)throw new IllegalArgumentException("Rating must be 1-5");
            observed=clean(observed,1100);
            expected=clean(expected,1100);
            explanation=clean(explanation,2200);
            if(observed.isBlank()||expected.isBlank())
                throw new IllegalArgumentException("Record expected AND observed behavior.");
            if(versions==null)throw new IllegalArgumentException("Version metadata required");
            if(REDSTONE.equals(category)){
                if(pillar==null||!PILLARS.contains(pillar))
                    throw new IllegalArgumentException("Choose a valid redstone pillar.");
                if(!CLAIMS.contains(verification))
                    throw new IllegalArgumentException("Invalid reported test status.");
            }else{
                // Aesthetic preferences are subjective and MUST NOT inherit
                // redstone verification/pillar labels or circuit trust scores.
                pillar="Not applicable";
                verification="Subjective preference";
            }
        }
        /** Compatibility constructor for pre-v2 local call sites/tests.
         * Unknown original versions stay explicit, never invented. */
        public Report(String pillar,String verification,int rating,
                      String observed,String expected,String explanation,
                      boolean rightsConfirmed){
            this(REDSTONE,pillar,verification,rating,observed,expected,explanation,
                Versions.unspecified(),rightsConfirmed);
        }
    }
    private static String clean(String text,int limit){
        if(text==null)return "";
        StringBuilder clean=new StringBuilder(text.length());
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            clean.append(Character.isISOControl(c)?' ':c);
        }
        String val=clean.toString().strip();
        if(val.length()>limit)
            throw new IllegalArgumentException("Feedback exceeds "+limit+" characters");
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
        report.addProperty("schema",SCHEMA);
        report.addProperty("schema_version",SCHEMA_VERSION);
        report.addProperty("category",feedback.category());
        report.addProperty("namespace",REDSTONE.equals(feedback.category())
            ?"redstone_insights":"build_preferences");
        // Immutable initial trust status: user reports are NEVER verified
        // training data, even when they claim a working correction.
        report.addProperty("review_status","quarantined");
        report.addProperty("trust_level","unverified");
        report.addProperty("eligible_for_shared_learning",false);
        report.addProperty("reviewer_verified",false);
        report.addProperty("minecraft_edition","Java");
        report.addProperty("minecraft_data_version",3955);
        report.addProperty("exporter_buildai_version",
            feedback.versions().exporterBuildAi());
        report.addProperty("exporter_source_revision",
            feedback.versions().exporterSourceRevision());
        report.addProperty("minecraft_version",feedback.versions().minecraft());
        report.addProperty("fabric_loader_version",
            feedback.versions().fabricLoader());
        report.addProperty("benchmark_id",feedback.versions().benchmarkId());
        report.addProperty("benchmark_revision",
            feedback.versions().benchmarkRevision());
        report.addProperty("source_generation_version","unknown");
        report.addProperty("pillar",feedback.pillar());
        report.addProperty("contributor_claim",feedback.verification());
        report.addProperty("rating",feedback.rating());
        report.addProperty("expected",feedback.expected());
        report.addProperty("observed",feedback.observed());
        report.addProperty("explanation",feedback.explanation());
        report.addProperty("generated_sha256",sha(generated));
        if(correction!=null)report.addProperty("corrected_sha256",sha(correction));
        report.addProperty("license",LICENSE);
        report.addProperty("rights_confirmed",true);
        report.addProperty("contains_contributor_claimed_correction",
            correction!=null&&REDSTONE.equals(feedback.category()));
        // Corrected geometry cannot become a 'verified solution' merely by
        // the contributor checking a box. Promotion is maintainer-only.
        report.addProperty("contains_verified_solution",false);
        report.addProperty("example_role",PREFERENCE.equals(feedback.category())
            ?"subjective_style_example":"unverified_redstone_evidence");
        String readme="""
            BuildAI community contribution, schema v2.

            This local export is QUARANTINED, not approved knowledge.
            report.json: contributor's account of observed vs expected results,
              schema & exporter version, pillar/category and SHA-256 hashes.
            generated.schem: de-identified source geometry, no sign text.
            corrected.schem (optional): de-identified contributor correction.

            REDSTONE INSIGHT is a functional engineering claim; an expert
            reviewer must independently reproduce the circuit in Java and
            confirm all relevant inputs, outputs, resets and mechanics.
            BUILD PREFERENCE describes one person's subjective design choices;
            it is never global truth or a functional redstone PASS.

            No prompt, auth token, original AI history, script, local paths,
            sign text, player/world identity or arbitrary tile NBT is exported.
            Check free-text feedback for personal details before posting.

            Required consent: you hold the rights to share these materials
            under Creative Commons Attribution 4.0.
            Attach this ZIP to the corresponding PUBLIC GitHub issue yourself.
            BuildAI does not upload files or ingest unreviewed submissions.
            """;
        Files.createDirectories(folder);
        String stem=(REDSTONE.equals(feedback.category())? "redstone-insight-":"build-preference-")+
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
        if(REDSTONE.equals(report.category())&&
           report.verification().equals("Corrected and tested")&&fixed==null)
            throw new IOException("Contributor-tested correction needs its corrected schematic");
        if(PREFERENCE.equals(report.category())&&fixed!=null){
            // Style before/after examples are allowed but are NOT circuit fixes.
        }
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
