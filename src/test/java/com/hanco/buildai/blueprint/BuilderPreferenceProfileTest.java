package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class BuilderPreferenceProfileTest {
    @Test void oldFeedbackStillInfluencesProfileAfterManyNewIterations(){
        List<BuilderPreferenceProfile.Review> history=new ArrayList<>();
        history.add(new BuilderPreferenceProfile.Review(5,"castle","Loved the varied roof outlines and stone trim"));
        for(int i=0;i<48;i++)history.add(new BuilderPreferenceProfile.Review(3,"build"+i,
            "Needs more usable interior floor access"));
        String profile=BuilderPreferenceProfile.summarize(history);
        assertTrue(profile.contains("Roofs and facades"),profile);
        assertTrue(profile.contains("roof outlines"),profile);
        assertTrue(profile.contains("Access and usability"));
        assertTrue(profile.contains("49 saved feedback iterations"));
    }
    @Test void mixedFeedbackDoesNotClaimAllElementsWereDisliked(){
        String p=BuilderPreferenceProfile.summarize(List.of(
            new BuilderPreferenceProfile.Review(2,"treehouse",
                "The canopy is excellent, but the staircase is too narrow")));
        assertTrue(p.contains("rating 2/5: The canopy is excellent"));
        assertTrue(p.contains("rating as contextual"));
    }
    @Test void profileIsBoundedAndSupportsUncategorizedFeedback(){
        List<BuilderPreferenceProfile.Review> all=new ArrayList<>();
        for(int i=0;i<120;i++)all.add(new BuilderPreferenceProfile.Review(5,"x",
            "Please improve roof canopy terrain windows details "+ "x".repeat(500)));
        assertTrue(BuilderPreferenceProfile.summarize(all).length()<=3600);
        assertTrue(BuilderPreferenceProfile.summarize(List.of(
            new BuilderPreferenceProfile.Review(3,"x","Strange but fun"))).contains("1 saved feedback"));
    }
}
