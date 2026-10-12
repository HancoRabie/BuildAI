package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Non-fatal functional-logic checklist, not an execution simulator. It flags
 * missing minigame subsystems so a good-looking arena cannot silently be
 * advertised as a working CTF server game.
 */
public final class CommandGameAudit {
    private CommandGameAudit(){}
    public static List<String> inspect(Blueprint blueprint,String playerPrompt) {
        if(blueprint.commands().isEmpty())return List.of();
        List<String> warnings=new ArrayList<>();
        List<String> commands=blueprint.commands().stream()
            .map(e->e.command().toLowerCase(Locale.ROOT)).toList();
        boolean ctf=playerPrompt!=null&&
            (playerPrompt.toLowerCase(Locale.ROOT).contains("capture the flag")||
             playerPrompt.toLowerCase(Locale.ROOT).contains("ctf"));
        if(!ctf)return List.of(
            "Command logic is static-checked only. Test buttons, selectors, scores, timers and reset in a disposable 1.21.1 world.");
        if(none(commands,"team add","team join","team modify"))
            warnings.add("CTF gameplay: no team setup/assignment commands detected.");
        if(none(commands,"scoreboard objectives add"))
            warnings.add("CTF gameplay: no scoreboard objective initialization detected.");
        if(none(commands,"scoreboard players add","scoreboard players set","scoreboard players operation"))
            warnings.add("CTF gameplay: no state/score mutation commands detected.");
        if(none(commands,"give ","effect give","item replace"))
            warnings.add("CTF gameplay: no functional loadout-grant commands detected.");
        if(none(commands,"execute ","scoreboard players test"))
            warnings.add("CTF gameplay: no trigger/flag-state detection commands detected.");
        if(none(commands,"tp ","teleport ","spawnpoint "))
            warnings.add("CTF gameplay: no lobby/spawn/reset positioning commands detected.");
        if(none(commands,"title ","tellraw ","playsound "))
            warnings.add("CTF gameplay: no clear start/win/team feedback detected.");
        warnings.add("CTF commands require in-game integration testing: team selection, setup, flag pick-up/return, scoring, win, loadouts, and full round reset.");
        return List.copyOf(warnings);
    }
    private static boolean none(List<String> commands,String... needles){
        for(String c:commands)for(String n:needles)if(c.contains(n))return false;
        return true;
    }
}
