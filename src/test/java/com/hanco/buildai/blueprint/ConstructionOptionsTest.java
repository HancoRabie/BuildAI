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
    @Test void hollowIsItsOwnInteriorOnlyMode(){
        assertEquals(new ConstructionOptions(true,false,true),
            ConstructionOptions.parse("hollow:yes"));
        assertEquals(new ConstructionOptions(false,false,true),
            ConstructionOptions.parse("foundation:no hollow:yes"));
        assertEquals(new ConstructionOptions(false,false,true),
            ConstructionOptions.parse("excavate:no foundation:no hollow:yes"));
        assertEquals(new ConstructionOptions(false,false,false),
            ConstructionOptions.parse("hollow:no excavate:no foundation:no"));
        assertThrows(IllegalArgumentException.class,
            ()->ConstructionOptions.parse("hollow:yes excavate:yes"));
        assertThrows(IllegalArgumentException.class,
            ()->ConstructionOptions.parse("hollow:yes hollow:no"));
    }
    @Test void identifiesMultipleBaseLayersIncludingAirPadding(){
        Blueprint b=new Blueprint("Tall mountain house",8,9,8);
        for(int y=1;y<=3;y++)for(int x=0;x<8;x++)for(int z=0;z<8;z++)
            b.set(x,y,z,"minecraft:stone_bricks");
        b.set(2,4,2,"minecraft:oak_stairs");
        b.set(3,4,2,"minecraft:lantern");
        var omission=ConstructionOptions.identifiedFoundation(b);
        assertEquals(192,omission.size(),"All three base layers should be omitted");
        assertFalse(omission.contains(b.index(2,4,2)),"Living area must survive");
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
