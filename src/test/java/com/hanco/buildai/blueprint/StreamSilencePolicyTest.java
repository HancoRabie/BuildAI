package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class StreamSilencePolicyTest {
    private static final long SEC=1_000_000_000L;
    @Test void acceptedHttp200CannotWaitForeverWithZeroDesignText(){
        long start=100*SEC;
        assertFalse(StreamSilencePolicy.expired(start+7*60*SEC,start,false));
        assertFalse(StreamSilencePolicy.expired(start+479*SEC,start,false));
        assertTrue(StreamSilencePolicy.expired(start+8*60*SEC,start,false));
    }
    @Test void streamWhichStopsAfterOutputHasShorterDeadline(){
        long received=100*SEC;
        assertFalse(StreamSilencePolicy.expired(received+179*SEC,received,true));
        assertTrue(StreamSilencePolicy.expired(received+180*SEC,received,true));
    }
    @Test void newTextResetsWatchdogButHeartbeatDoesNot(){
        long sent=100*SEC;
        assertFalse(StreamSilencePolicy.expired(sent+100*SEC,sent,true));
        assertTrue(StreamSilencePolicy.expired(sent+181*SEC,sent,true));
        assertFalse(StreamSilencePolicy.expired(sent+181*SEC,sent+150*SEC,true));
    }
    @Test void disablingAutoStopAllowsLongThinkingWithoutSilentCancellation(){
        long start=100*SEC;
        long now=start+26*60*SEC;
        assertTrue(StreamSilencePolicy.expired(now,start,false,true,0L));
        assertFalse(StreamSilencePolicy.expired(now,start,false,false,0L),
            "User disabled the limit: 26 minutes silent must NOT auto-cancel");
        assertFalse(StreamSilencePolicy.expired(now,start,true,false,0L),
            "User disabled the limit after text: 3-minute deadline also bypassed");
    }
    @Test void reEnablingAfterADeadlineStartsFullFreshWindow(){
        long firstText=50*SEC;
        long reenabled=firstText+18*60*SEC;
        assertFalse(StreamSilencePolicy.expired(reenabled,firstText,false,true,reenabled),
            "Turning auto-stop back on must NOT kill the same stream immediately");
        assertFalse(StreamSilencePolicy.expired(
            reenabled+479*SEC,firstText,false,true,reenabled));
        assertTrue(StreamSilencePolicy.expired(
            reenabled+480*SEC,firstText,false,true,reenabled));
        assertFalse(StreamSilencePolicy.expired(
            reenabled+179*SEC,firstText,true,true,reenabled));
        assertTrue(StreamSilencePolicy.expired(
            reenabled+180*SEC,firstText,true,true,reenabled));
    }
    @Test void actualModelTextResetsTimeoutEvenAfterReEnabling(){
        long start=10*SEC;
        long reenabled=1000*SEC;
        long newText=reenabled+120*SEC;
        assertFalse(StreamSilencePolicy.expired(
            newText+179*SEC,newText,true,true,reenabled));
        assertTrue(StreamSilencePolicy.expired(
            newText+180*SEC,newText,true,true,reenabled));
    }

}
