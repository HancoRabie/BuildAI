package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

public class ConstructionOptionsTest {
    @Test void defaultModeKeepsFullExcavationAndBase(){
        assertEquals(new ConstructionOptions(true,true),
            ConstructionOptions.parse(""));
        assertEquals(ConstructionOptions.DEFAULT,ConstructionOptions.parse(null));
        assertEquals(ConstructionOptions.DEFAULT,
            ConstructionOptions.parse("foundation:yes excavate:yes"));
    }
    @Test void acceptsIndependentAndReorderedFlags(){
        assertEquals(new ConstructionOptions(false,false),
            ConstructionOptions.parse("foundation:no excavate:no"));
        assertEquals(new ConstructionOptions(false,false),
            ConstructionOptions.parse("excavate:no foundation:no"));
        assertEquals(new ConstructionOptions(true,false),
            ConstructionOptions.parse("excavate:no"));
        assertEquals(new ConstructionOptions(false,true),
            ConstructionOptions.parse("foundation:no"));
    }
    @Test void rejectsUnsafeTyposAndDuplicates(){
        for(String bad:new String[]{"foundation:maybe","excavate:false",
            "force:yes","foundation:no foundation:yes","foundation:",
            "excavate:no:yes","no","excavate:no unknown:yes"}){
            assertThrows(IllegalArgumentException.class,
                ()->ConstructionOptions.parse(bad),bad);
        }
    }
    @Test void detectsObviousFourByFourSolidPlatformButKeepsStatue(){
        Blueprint b=new Blueprint("Mountain statue",6,10,6);
        for(int z=0;z<6;z++)for(int x=0;x<6;x++)
            b.set(x,0,z,"minecraft:stone_bricks");
        b.set(2,1,2,"minecraft:gold_block"); // statue legs
        b.set(3,1,2,"minecraft:gold_block");
        Set<Integer> removed=ConstructionOptions.identifiedFoundation(b);
        assertEquals(36,removed.size());
        assertTrue(removed.contains(b.index(0,0,0)));
        assertFalse(removed.contains(b.index(2,1,2)));
    }
    @Test void doesNotDeleteArtFirstRowOrMechanicalFirstLayer(){
        Blueprint pixel=new Blueprint("Bee artwork",1,20,30);
        for(int z=0;z<30;z++)
            pixel.set(0,0,z,"minecraft:black_concrete");
        assertTrue(ConstructionOptions.identifiedFoundation(pixel).isEmpty());
        Blueprint machine=new Blueprint("Machine",5,3,5);
        for(int z=0;z<5;z++)for(int x=0;x<5;x++)
            machine.set(x,0,z,"minecraft:stone_bricks");
        machine.set(2,0,2,"minecraft:redstone_wire");
        assertTrue(ConstructionOptions.identifiedFoundation(machine).isEmpty());
    }
    @Test void refusesUncertainBasesRatherThanRemovingSculptureFeet(){
        Blueprint legs=new Blueprint("Feet",10,8,10);
        for(int z=0;z<10;z++)for(int x=0;x<10;x++)
            if(x==2||x==7)
                legs.set(x,0,z,"minecraft:stone_bricks");
        assertTrue(ConstructionOptions.identifiedFoundation(legs).isEmpty());
    }
}
