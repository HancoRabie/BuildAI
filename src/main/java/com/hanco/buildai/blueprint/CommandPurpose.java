package com.hanco.buildai.blueprint;

import java.util.Locale;

/**
 * Short, auditable explanations inferred from command syntax for the approval
 * screen. These are heuristics, NOT promises that the command is correct.
 */
public final class CommandPurpose {
    private CommandPurpose(){}
    public static String summary(String command) {
        if(command==null||command.isBlank())return "Empty command — not functional";
        String s=command.trim().toLowerCase(Locale.ROOT);
        if(s.startsWith("scoreboard objectives add"))return "Create a game-state or scoring objective";
        if(s.startsWith("scoreboard objectives remove"))return "Reset a game-state objective";
        if(s.startsWith("scoreboard players set"))return "Initialize or set a tracked game-state value";
        if(s.startsWith("scoreboard players add"))return "Increase a tracked score or counter";
        if(s.startsWith("scoreboard players operation"))return "Calculate or transfer a game-state value";
        if(s.startsWith("scoreboard"))return "Manage tracked game scores and state";
        if(s.startsWith("team add"))return "Create a team for multiplayer gameplay";
        if(s.startsWith("team join"))return "Assign players to a game team";
        if(s.startsWith("team modify"))return "Configure team color or rules";
        if(s.startsWith("team"))return "Manage teams and team membership";
        if(s.startsWith("execute")){
            if(s.contains(" if score ")||s.contains(" unless score "))
                return "Check game conditions before running an action";
            if(s.contains(" if entity ")||s.contains(" unless entity "))
                return "Detect selected players or entities for game logic";
            return "Run a game action in a specific player/world context";
        }
        if(s.startsWith("title "))return "Show a player HUD, action-bar or game announcement";
        if(s.startsWith("tellraw "))return "Show formatted player instructions or feedback";
        if(s.startsWith("give "))return "Provide a player loadout, reward or item";
        if(s.startsWith("clear "))return "Remove items during reset or loadout changes";
        if(s.startsWith("effect "))return "Grant or remove a player status effect";
        if(s.startsWith("tp ")||s.startsWith("teleport "))return "Move players to lobby, arena or reset location";
        if(s.startsWith("spawnpoint "))return "Set a respawn point for participating players";
        if(s.startsWith("playsound "))return "Play an audio cue for a game event";
        if(s.startsWith("particle "))return "Display a visual cue or effect";
        if(s.startsWith("time "))return "Control world daytime for the build";
        if(s.startsWith("weather "))return "Control rain, thunder or clear weather";
        if(s.startsWith("gamerule "))return "Configure daylight or weather cycling";
        if(s.startsWith("tag "))return "Track players or entities with gameplay tags";
        return "Game mechanic — inspect full command before approval";
    }
}
