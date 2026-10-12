package com.hanco.buildai.blueprint;

import java.nio.file.Path;

/**
 * Stable, normalized local identity of a reviewed schematic.
 * Historical v0.1 reviews only recorded a filename; those can be read from
 * the original saved-blueprint folder but never assigned to arbitrary imports.
 */
public final class ReviewIdentity {
    private ReviewIdentity(){}
    public static String key(Path source){
        return source.toAbsolutePath().normalize().toString();
    }
    public static boolean matches(String recordedPath,Path candidate){
        return recordedPath!=null&&recordedPath.equals(key(candidate));
    }
    public static boolean legacyMatches(String filename,Path candidate,Path savedDirectory){
        return filename!=null && candidate!=null && savedDirectory!=null &&
            filename.equals(candidate.getFileName().toString()) &&
            candidate.toAbsolutePath().normalize().getParent()
                .equals(savedDirectory.toAbsolutePath().normalize());
    }
}
