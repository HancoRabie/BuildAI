package com.hanco.buildai.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

public class ModelStreamDraftJournalTest {
    @Test void partialStreamSurvivesCloseAndRetainsExactlyReceivedText(@TempDir Path dir)
        throws Exception {
        ModelStreamDraftJournal journal=new ModelStreamDraftJournal(dir);
        String a="{\"name\":\"Benchy\",\"operations\":[";
        String b="{\"kind\":\"fill\"}";
        journal.append(a);
        journal.append(b);
        journal.close();
        try(var entries=Files.list(dir)){
            Path draft=entries.findFirst().orElseThrow();
            assertTrue(draft.getFileName().toString().endsWith(".json.part"));
            assertEquals(a+b,Files.readString(draft,StandardCharsets.UTF_8));
            assertTrue(journal.recoveryNotice().contains(draft.getFileName().toString()));
        }
    }

    @Test void acceptedDraftDeletesRecoveryCopy(@TempDir Path dir) throws Exception {
        ModelStreamDraftJournal journal=new ModelStreamDraftJournal(dir);
        journal.append("{\"name\":\"Valid\"}");
        journal.close();
        journal.discard();
        try(var files=Files.list(dir)){
            assertEquals(0,files.count());
        }
    }

    @Test void emptyStreamDoesNotCreateMisleadingDraft(@TempDir Path dir)
        throws Exception {
        ModelStreamDraftJournal journal=new ModelStreamDraftJournal(dir);
        journal.append("");
        journal.close();
        try(var files=Files.list(dir)){
            assertEquals(0,files.count());
        }
    }
}