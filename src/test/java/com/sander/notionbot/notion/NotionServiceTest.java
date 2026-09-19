package com.sander.notionbot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sander.notionbot.notion.model.NotionPage;
import com.sander.notionbot.notion.model.NotionProperty;
import com.sander.notionbot.notion.model.PenaltyBalance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String value) throws Exception {
        return MAPPER.readTree(value.replace('\'', '"'));
    }

    @Test
    void rollupNumberBecomesNumeric() throws Exception {
        NotionProperty property = NotionService.toProperty(json(
                "{'type':'rollup','rollup':{'type':'number','number':9,'function':'sum'}}"));
        assertEquals(new NotionProperty.Numeric(new BigDecimal("9")), property);
    }

    @Test
    void formulaBooleanBecomesCheckbox() throws Exception {
        NotionProperty property = NotionService.toProperty(json(
                "{'type':'formula','formula':{'type':'boolean','boolean':true}}"));
        assertEquals(new NotionProperty.Checkbox(true), property);
    }

    @Test
    void createdTimeBecomesDate() throws Exception {
        NotionProperty property = NotionService.toProperty(json(
                "{'type':'created_time','created_time':'2026-09-18T10:15:00.000Z'}"));
        assertEquals(new NotionProperty.DateRange("2026-09-18T10:15:00.000Z", null), property);
    }

    @Test
    void emptyRollupCountsAsZero() {
        PenaltyBalance balance = NotionService.toBalance(member(new NotionProperty.Numeric(null)));
        assertEquals(0, balance.unredeemed());
    }

    @Test
    void missingUnredeemedPropertyFailsLoudly() {
        NotionPage member = new NotionPage("id", "url", Instant.EPOCH, Instant.EPOCH,
                Map.of("Navn", new NotionProperty.Title("Ola")));
        assertThrows(IllegalStateException.class, () -> NotionService.toBalance(member));
    }

    private static NotionPage member(NotionProperty unredeemed) {
        return new NotionPage("id", "url", Instant.EPOCH, Instant.EPOCH, Map.of(
                "Navn", new NotionProperty.Title("Ola"),
                NotionService.UNREDEEMED_PROPERTY, unredeemed));
    }
}
