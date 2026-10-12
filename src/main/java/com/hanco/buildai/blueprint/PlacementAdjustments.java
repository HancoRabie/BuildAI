package com.hanco.buildai.blueprint;

/**
 * Deterministic Minecraft-camera-relative placement moves.
 * Direction is captured once on G, so walking around to inspect the preview
 * doesn't make the LEFT/RIGHT/FORWARD/BACK controls suddenly change axes.
 */
public final class PlacementAdjustments {
    private PlacementAdjustments(){}
    public record Move(int x,int y,int z){}
    public static Move forward(float yaw) {
        int quadrant=Math.floorMod(Math.round(yaw/90f),4);
        return switch(quadrant) {
            case 1 -> new Move(-1,0,0); // west
            case 2 -> new Move(0,0,-1); // north
            case 3 -> new Move(1,0,0); // east
            default -> new Move(0,0,1); // south
        };
    }
    public static Move nudge(float lockedYaw,int key,boolean control){
        Move f=forward(lockedYaw);
        if(control) {
            return switch(key) {
                case 2 -> f; // CTRL+UP: forward
                case -2 -> new Move(-f.x(),0,-f.z()); // CTRL+DOWN: backward
                default -> new Move(0,0,0);
            };
        }
        return switch(key){
            case -1 -> new Move(f.z(),0,-f.x()); // LEFT
            case 1 -> new Move(-f.z(),0,f.x()); // RIGHT
            case 2 -> new Move(0,1,0); // UP
            case -2 -> new Move(0,-1,0); // DOWN
            default -> new Move(0,0,0);
        };
    }
}
