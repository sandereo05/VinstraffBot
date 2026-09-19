package com.sander.notionbot.slack.handlers;

import com.sander.notionbot.notion.model.PenaltyBalance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrafferCommandHandlerTest {

    @Test
    void formatsTableWithTotalsAndRecommended() {
        String text = StrafferCommandHandler.format(List.of(
                new PenaltyBalance("Ola Nordmann", 14),
                new PenaltyBalance("Kari", 3)));

        assertTrue(text.contains("2 personer, 17 totalt"), text);
        assertTrue(text.contains("Ola Nordmann            14         6"), text);
        assertTrue(text.contains("Kari                     3         1"), text);
    }

    @Test
    void emptyListSaysNoPenalties() {
        assertEquals("Ingen ikke-innløste vinstraffer :tada:", StrafferCommandHandler.format(List.of()));
    }
}
