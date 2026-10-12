package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlacementAdjustmentsTest {
    @Test void arrowsWorkInCapturedSouthFacingFrame(){
        assertEquals(new PlacementAdjustments.Move(1,0,0),
            PlacementAdjustments.nudge(0,-1,false));
        assertEquals(new PlacementAdjustments.Move(-1,0,0),
            PlacementAdjustments.nudge(0,1,false));
        assertEquals(new PlacementAdjustments.Move(0,1,0),
            PlacementAdjustments.nudge(0,2,false));
        assertEquals(new PlacementAdjustments.Move(0,-1,0),
            PlacementAdjustments.nudge(0,-2,false));
        assertEquals(new PlacementAdjustments.Move(0,0,1),
            PlacementAdjustments.nudge(0,2,true));
        assertEquals(new PlacementAdjustments.Move(0,0,-1),
            PlacementAdjustments.nudge(0,-2,true));
    }
    @Test void cameraRelativeControlsWorkAtAllFourYawOrientations(){
        for(int i=0;i<4;i++){
            float yaw=i*90;
            var forward=PlacementAdjustments.forward(yaw);
            var back=PlacementAdjustments.nudge(yaw,-2,true);
            var left=PlacementAdjustments.nudge(yaw,-1,false);
            var right=PlacementAdjustments.nudge(yaw,1,false);
            assertEquals(new PlacementAdjustments.Move(-forward.x(),0,-forward.z()),back);
            assertEquals(new PlacementAdjustments.Move(-left.x(),0,-left.z()),right);
            assertEquals(0,left.x()*forward.x()+left.z()*forward.z());
            assertEquals(1,Math.abs(forward.x())+Math.abs(forward.z()));
        }
    }
    @Test void headingIsStableForEquivalentAngles(){
        assertEquals(PlacementAdjustments.forward(0),PlacementAdjustments.forward(360));
        assertEquals(PlacementAdjustments.forward(-90),PlacementAdjustments.forward(270));
    }
}
