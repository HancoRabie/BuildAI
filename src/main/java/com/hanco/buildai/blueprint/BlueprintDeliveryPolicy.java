package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.Locale;

/**
 * A model's "I cannot test/preload this" is not a geometrical impossibility.
 * Recover once from this specific response format, while preserving real
 * size limits and remaining honest about unavailable block entity state.
 */
public final class BlueprintDeliveryPolicy {
    private BlueprintDeliveryPolicy(){}

    public static boolean retryableLimitation(String response){
        if(response==null||response.isBlank())return false;
        String message=response.trim();
        try{
            JsonElement parsed=JsonParser.parseString(message);
            if(!parsed.isJsonObject())return false;
            JsonObject root=parsed.getAsJsonObject();
            // The model already delivered usable operations: compile them
            // and present its limitations as warnings instead.
            if(root.has("operations")&&root.get("operations").isJsonArray()&&
                !root.getAsJsonArray("operations").isEmpty())return false;
            if(!root.has("error")||!root.get("error").isJsonPrimitive())return false;
            message=root.get("error").getAsString();
        }catch(JsonParseException ignored){
            // Invalid free-form refusal is a model error, not a valid design.
        }
        String lower=message.toLowerCase(Locale.ROOT);
        // NEVER override a real engine limit by silently shrinking/omitting.
        if(lower.contains("4,000,000")||lower.contains("4000000")||
           lower.contains("16,000")||lower.contains("16000")||
           lower.contains("512 block")||lower.contains("section-based")||
           lower.contains("exceeds the")||lower.contains("outside bounds")||
           lower.contains("too many cells"))return false;
        boolean refusal=lower.contains("cannot")||lower.contains("unable")||
            lower.contains("can't")||lower.contains("not possible")||
            lower.contains("not available")||lower.contains("cannot truthfully");
        boolean validationExcuse=lower.contains("validat")||
            lower.contains("test")||lower.contains("simulat")||
            lower.contains("guarantee")||lower.contains("verify")||
            lower.contains("fully functional")||
            lower.contains("preload")||lower.contains("pre-load")||
            lower.contains("inventory")||lower.contains("block-only")||
            lower.contains("block entity")||lower.contains("block-entity");
        return refusal&&validationExcuse;
    }

    /** Keep prior refusal as a *category*, not a prior assistant turn in a new
     * model request. This never forwards external text/instructions.
     */
    public static String recoveryForResponse(String previousResponse){
        String category=previousResponse==null?"":previousResponse;
        try{
            JsonElement parsed=JsonParser.parseString(category);
            if(parsed.isJsonObject()){
                JsonObject obj=parsed.getAsJsonObject();
                if(obj.has("error")&&obj.get("error").isJsonPrimitive())
                    category=obj.get("error").getAsString();
            }
        }catch(JsonParseException ignored){}
        String guidance=suggestClarification(category);
        return correction()+(!guidance.isBlank()
            ?"\nSPECIFIC ENGINEERING CORRECTION FOR THIS NEW, INDEPENDENT "+
             "REQUEST:\n"+guidance
            :"\nPREVIOUS ATTEMPT NEEDED ACTUAL BLUEPRINT GEOMETRY, "+
             "NOT A CAVEAT-ONLY RESPONSE.");
    }
    public static String shortReason(String previousResponse){
        if(previousResponse==null)return "";
        try{
            JsonElement obj=JsonParser.parseString(previousResponse);
            if(obj.isJsonObject()&&obj.getAsJsonObject().has("error"))
                previousResponse=obj.getAsJsonObject().get("error").getAsString();
        }catch(JsonParseException ignored){}
        String normalized=previousResponse.replaceAll("[\\r\\n\\t]+"," ");
        return normalized.length()>400?normalized.substring(0,400):normalized;
    }

    /** User-visible, editable suggestion derived from model failure CATEGORY,
     * never a copied assistant transcript or opaque model "memory".
     */
    public static String suggestClarification(String failedReason){
        if(failedReason==null||failedReason.isBlank())return "";
        String lower=failedReason.toLowerCase(Locale.ROOT);
        if(lower.contains("inventory")||lower.contains("preload")||
           lower.contains("block-only")||lower.contains("block entity")||
           lower.contains("block-entity"))
            return "Design the complete physical structure and functioning "+
                "redstone layout now. Use viable alternatives where inventory "+
                "NBT cannot be preloaded; clearly list any specific in-game "+
                "configuration required instead of refusing the build.";
        if(lower.contains("validat")||lower.contains("test")||
           lower.contains("verif")||lower.contains("guarantee"))
            return "Generate a complete buildable engineering proposal. "+
                "Do not refuse because in-world validation is pending; "+
                "I will test the actual machine in Minecraft and give feedback.";
        return "";
    }

    public static String correction(){
        return """
            BUILDAI GENERATION RECOVERY — ONE RETRY, ORIGINAL REQUEST UNCHANGED:
            Your previous response explained that the mechanism cannot be
            verified, preloaded, or tested through a block-only schematic.
            That is NOT a reason to return {"error":...}. BuildAI generates
            engineering candidates in Minecraft; the player tests them.
            DO NOT claim any circuit has already been tested. Return a
            COMPLETE JSON blueprint at the same scale with actual valid
            operations and full requested geometry. Design the highest-quality
            realizable mechanism using the redstone technical handbook.
            Include the requested irrigation, gates, manual controls,
            collection conduits/chests and real pre-planted mature crops
            where supported by block states. If an inventory-dependent
            device needs real item/NBT setup (e.g., hopper clock or
            dispenser), prefer a capable alternative with no missing
            inventory dependency if that meets the requirements. Otherwise
            include precise short strings under optional JSON "limitations"
            describing each missing item/setup and what to test. Never
            quietly omit a requested crop/system or describe it as proven.
            Never send explanations outside JSON. The only valid reason for
            an "error" is a hard engine size/operation bound that cannot be
            met without violating the user's required dimensions.
            """;
    }
}
