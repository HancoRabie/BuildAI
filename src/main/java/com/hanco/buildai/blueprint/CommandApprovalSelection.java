package com.hanco.buildai.blueprint;

import java.util.*;

/** Exact 1-based command selection shown identically in chat and review GUI. */
public final class CommandApprovalSelection {
    private CommandApprovalSelection(){}

    public static Set<Integer> all(int size){
        if(size<1 || size>CommandBlockPlan.MAX_COMMANDS)
            throw new IllegalArgumentException("No valid command plan to approve");
        Set<Integer> selected=new LinkedHashSet<>();
        for(int i=0;i<size;i++)selected.add(i);
        return Collections.unmodifiableSet(selected);
    }

    public static Set<Integer> parse(String raw,int size) {
        if(raw==null || raw.isBlank())
            throw new IllegalArgumentException("Specify command numbers, e.g. 1,2,4,7, or use approve all");
        if(size<1 || size>CommandBlockPlan.MAX_COMMANDS)
            throw new IllegalArgumentException("No valid command plan to approve");
        Set<Integer> selected=new TreeSet<>();
        for(String value:raw.split(",",-1)){
            String number=value.trim();
            if(!number.matches("[0-9]{1,3}"))
                throw new IllegalArgumentException("Invalid number '"+number+"'. Use 1-based command numbers separated by commas");
            int oneBased=Integer.parseInt(number);
            if(oneBased<1 || oneBased>size)
                throw new IllegalArgumentException("Command #"+oneBased+" does not exist (valid: 1-"+size+")");
            selected.add(oneBased-1);
        }
        return Collections.unmodifiableSet(selected);
    }
}
