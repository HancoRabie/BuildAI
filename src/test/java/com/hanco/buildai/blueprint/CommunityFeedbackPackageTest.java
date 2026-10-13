package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

public class CommunityFeedbackPackageTest {
    private static CommunityFeedbackPackage.Report report(){
        return new CommunityFeedbackPackage.Report(
            "Boolean logic","Corrected and tested",5,
            "XOR: 11 illuminated both outputs when it should not",
            "XOR: 00 off, 01 on, 10 on, 11 off",
            "Verified all four states twice in Java 1.21.1",true);
    }
    @Test void stripsSensitiveSchematicMetadataAndKeepsBlockStates(){
        Blueprint source=new Blueprint("my private server and account",6,4,7);
        source.set(2,1,3,"minecraft:repeater[facing=north,powered=false]");
        source.set(3,1,3,"minecraft:oak_sign");
        source.setGenerationContext("secret original prompt","private AI JSON");
        source.setSigns(List.of(new SignTextPlan.Entry(3,1,3,
            List.of("PRIVATE TEXT"),List.of())));
        Blueprint out=CommunityFeedbackPackage.anonymizedGeometry(source);
        assertEquals("community_submission",out.name);
        assertEquals(source.get(2,1,3),out.get(2,1,3));
        assertFalse(out.hasOriginalPrompt());
        assertFalse(out.hasRevisionContext());
        assertTrue(out.signs().isEmpty());
        assertTrue(out.commands().isEmpty());
    }
    @Test void publicZipContainsOnlyApprovedFixedNamesAndSafeJson(@TempDir Path dir)
        throws Exception {
        byte[] original={1,2,3,4};
        byte[] corrected={5,6,7};
        Path zip=CommunityFeedbackPackage.packageBytes(dir,report(),
            original,corrected);
        Set<String> names=new TreeSet<>();
        String json="";
        try(ZipInputStream in=new ZipInputStream(Files.newInputStream(zip))){
            ZipEntry entry;
            while((entry=in.getNextEntry())!=null){
                names.add(entry.getName());
                byte[] bytes=in.readAllBytes();
                if(entry.getName().equals("report.json"))
                    json=new String(bytes,StandardCharsets.UTF_8);
                if(entry.getName().equals("generated.schem"))
                    assertArrayEquals(original,bytes);
                if(entry.getName().equals("corrected.schem"))
                    assertArrayEquals(corrected,bytes);
            }
        }
        assertEquals(Set.of("report.json","README.txt","generated.schem",
            "corrected.schem"),names);
        assertTrue(json.contains("buildai-community-example-v1"));
        assertTrue(json.contains("Corrected and tested"));
        assertFalse(json.contains(dir.toString()));
        assertFalse(json.contains("projectPath"));
        assertFalse(json.contains("account"));
        assertTrue(json.contains("corrected_sha256"));
    }
    @Test void rejectUnsignedUnreproducibleAndInvalidRatings(){
        assertThrows(IllegalArgumentException.class,()->
            new CommunityFeedbackPackage.Report("Boolean logic",
                "Failure observed",3,"broken","working","",false));
        assertThrows(IllegalArgumentException.class,()->
            new CommunityFeedbackPackage.Report("Boolean logic",
                "Failure observed",6,"broken","working","",true));
        assertThrows(IllegalArgumentException.class,()->
            new CommunityFeedbackPackage.Report("Boolean logic",
                "Failure observed",3,"","working","",true));
    }
    @Test void failureOnlyCaseDoesNotPretendToHaveWorkingCorrection(@TempDir Path dir)
        throws Exception{
        var report=new CommunityFeedbackPackage.Report("Other",
            "Failure observed",1,"lamp always on","lamp off",
            "first case failed",true);
        var zip=CommunityFeedbackPackage.packageBytes(dir,report,
            new byte[]{9},null);
        try(ZipInputStream in=new ZipInputStream(Files.newInputStream(zip))){
            int schematicCount=0;boolean corrected=false;
            ZipEntry e;
            while((e=in.getNextEntry())!=null){
                if(e.getName().endsWith(".schem"))schematicCount++;
                if(e.getName().equals("corrected.schem"))corrected=true;
            }
            assertEquals(1,schematicCount);
            assertFalse(corrected);
        }
    }
}
