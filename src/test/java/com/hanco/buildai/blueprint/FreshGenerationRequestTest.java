package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FreshGenerationRequestTest {
    @Test void retainsExactInstructionAndAddsEditableClarification(){
        String original="Build a working one-button eight-crop farm.";
        String revision="Hold flood gates open long enough; use legal pistons.";
        String merged=FreshGenerationRequest.merge(original,revision);
        assertTrue(merged.startsWith(original));
        assertTrue(merged.contains(revision));
        assertTrue(merged.contains("Additional clarification for this new build:"));
        assertFalse(merged.contains("previous_response_id"));
    }
    @Test void noClarificationReturnsUnchangedPrompt(){
        assertEquals("A cool house",FreshGenerationRequest.merge(" A cool house ","  "));
    }
    @Test void clearingOriginalDoesNotGenerateASubstituteFromOldContext(){
        assertThrows(IllegalArgumentException.class,()->
            FreshGenerationRequest.merge("","Clarify redstone"));
        assertThrows(IllegalArgumentException.class,()->
            FreshGenerationRequest.merge(null,"Something else"));
    }
    @Test void rejectsTooLongCombinedPromptWithoutTruncating(){
        String longRequest="x".repeat(3980);
        var problem=assertThrows(IllegalArgumentException.class,()->
            FreshGenerationRequest.merge(longRequest,"Some additional information"));
        assertTrue(problem.getMessage().contains("maximum 4000"));
    }
    @Test void refusalCategoryMakesShortCorrectionNotOldAssistantTranscript(){
        String response="{\"error\":\"I cannot truthfully deliver a fully validated "+
            "build with block-only inventory configuration and in-world testing.\"}";
        String correction=BlueprintDeliveryPolicy.recoveryForResponse(response);
        assertTrue(correction.contains("COMPLETE JSON blueprint"));
        assertTrue(correction.contains("inventory"));
        assertFalse(correction.contains("I cannot truthfully"));
        assertFalse(correction.contains("previous_response_id"));
    }
}
