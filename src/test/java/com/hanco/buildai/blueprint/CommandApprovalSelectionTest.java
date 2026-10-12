package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class CommandApprovalSelectionTest {
    @Test void allMeansExactlyEveryScript(){
        assertEquals(Set.of(0,1,2,3),CommandApprovalSelection.all(4));
    }
    @Test void commaSeparatedHumanNumbersAreMappedToZeroBasedOnlyOnce(){
        assertEquals(Set.of(0,1,3,6,7,8),
            CommandApprovalSelection.parse("1, 2,4,7,8,9",10));
        assertEquals(Set.of(0,3),CommandApprovalSelection.parse("4, 1,4",4));
    }
    @Test void rejectsInvalidIndexOrMalformedSelectionInsteadOfPartialApproval(){
        for(String bad:new String[]{"","0","5","1,","-1","1;2","all","1,x","999","1,,2"}){
            assertThrows(IllegalArgumentException.class,
                ()->CommandApprovalSelection.parse(bad,4),bad);
        }
        assertThrows(IllegalArgumentException.class,()->CommandApprovalSelection.all(0));
    }
}
