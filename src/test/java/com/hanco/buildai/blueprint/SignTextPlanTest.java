package com.hanco.buildai.blueprint;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class SignTextPlanTest {
    private Blueprint sample(){
        Blueprint b=new Blueprint("Sign Test",5,5,5);
        b.set(1,2,3,"minecraft:oak_sign[rotation=0,waterlogged=false]");
        b.set(2,2,3,"minecraft:oak_wall_sign[facing=north,waterlogged=false]");
        return b;
    }

    @Test void compilesLiteralFrontAndBackLinesAgainstActualSigns(){
        String input="""
            [{"x":1,"y":2,"z":3,"lines":["01","NORTH","1-tick","Signal"]},
             {"x":2,"y":2,"z":3,"lines":["Lab"],"back_lines":["Back"]}]
            """;
        Blueprint b=sample();
        b.setSigns(SignTextPlan.decode(JsonParser.parseString(input).getAsJsonArray(),b));
        assertEquals(2,b.signs().size());
        assertEquals(List.of("01","NORTH","1-tick","Signal"),b.signs().get(0).front());
        assertEquals(List.of("Lab","","",""),b.signs().get(1).front());
        assertEquals(List.of("Back","","",""),b.signs().get(1).back());
        Blueprint clone=BlueprintJson.decode(BlueprintJson.encode(b));
        assertEquals(b.signs(),clone.signs());
    }

    @Test void forbidsNonSignCoordsDuplicateLabelsAndInjectedComponents(){
        Blueprint b=sample();
        assertThrows(IllegalArgumentException.class,()->
            SignTextPlan.decode(JsonParser.parseString(
                "[{\"x\":0,\"y\":0,\"z\":0,\"lines\":[\"Danger\"]}]")
                .getAsJsonArray(),b));
        assertThrows(IllegalArgumentException.class,()->
            SignTextPlan.decode(JsonParser.parseString(
                "[{\"x\":1,\"y\":2,\"z\":3,\"lines\":[\"A\"]},"+
                "{\"x\":1,\"y\":2,\"z\":3,\"lines\":[\"B\"]}]")
                .getAsJsonArray(),b));
        assertThrows(IllegalArgumentException.class,()->
            SignTextPlan.decode(JsonParser.parseString(
                "[{\"x\":1,\"y\":2,\"z\":3,\"lines\":[{\"clickEvent\":\"bad\"}]}]")
                .getAsJsonArray(),b));
        assertThrows(IllegalArgumentException.class,()->
            new SignTextPlan.Entry(1,2,3,List.of("A".repeat(25)),List.of()));
        assertThrows(IllegalArgumentException.class,()->
            new SignTextPlan.Entry(1,2,3,List.of("Line\nInjected"),List.of()));
    }

    @Test void rotationAndNetworkCarrySigns() {
        Blueprint b=sample();
        b.setSigns(SignTextPlan.decode(JsonParser.parseString(
            "[{\"x\":1,\"y\":2,\"z\":3,\"lines\":[\"01\",\"NORTH\"]}]")
            .getAsJsonArray(),b));
        // JUnit's plain JVM cannot bootstrap Fabric's patched Minecraft
        // registry. Test the SAME metadata transform used by the native
        // BlueprintRotator without trying to initialize registry globals.
        SignTextPlan.Entry sign=SignTextPlan.rotated(b,1).get(0);
        assertEquals(5-1-3,sign.x());
        assertEquals(1,sign.z());
        assertEquals("01",sign.front().get(0));
        // .schem export/import uses Minecraft's native block registry and
        // is exercised in an actual runClient/integrated-world session.
        // Pure JUnit only certifies literal metadata round-trips.
        Blueprint transported=BlueprintJson.decode(BlueprintJson.encode(b));
        assertEquals(b.signs(),transported.signs());
    }
}
