package com.hanco.buildai.blueprint;

/** Stable, testable thresholds for silent model streams after HTTP 200.
 * HTTP request timeout may not bound an already accepted streaming body.
 * A model reasoning event is NOT blueprint text and does not reset the clock.
 */
public final class StreamSilencePolicy {
    private StreamSilencePolicy(){}
    public static final long NO_TEXT_NANOS=8L*60*1_000_000_000L;
    public static final long AFTER_TEXT_NANOS=3L*60*1_000_000_000L;
    public static boolean expired(long now,long lastText,boolean everReceived){
        return now-lastText >= (everReceived?AFTER_TEXT_NANOS:NO_TEXT_NANOS);
    }
}
