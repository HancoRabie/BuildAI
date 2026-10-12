package com.hanco.buildai.blueprint;

/**
 * Deterministic prompt preparation for a NEW model request. This is not
 * "continue the conversation": no previous assistant transcript/response
 * ID or previous blueprint is attached. Genuine blueprint revisions remain
 * a separate explicit operation in BuildAI.
 */
public final class FreshGenerationRequest {
    private FreshGenerationRequest(){}
    public static String merge(String original,String clarification){
        String base=original==null?"":original.trim();
        String extra=clarification==null?"":clarification.trim();
        if(base.isEmpty())
            throw new IllegalArgumentException("Original building prompt is missing.");
        String combined=base+(extra.isEmpty()?"":
            "\n\nAdditional clarification for this new build:\n"+extra);
        if(combined.length()>4000)
            throw new IllegalArgumentException("Combined request is "+
                combined.length()+" characters; maximum 4000.");
        return combined;
    }
}
