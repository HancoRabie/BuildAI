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
}
