package com.sander.notionbot.notion.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record NotionPage(
        String id,
        String url,
        Instant createdTime,
        Instant lastEditedTime,
        Map<String, NotionProperty> properties) {

    public NotionPage {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /** Every Notion database has exactly one title property. */
    public String title() {
        return properties.values().stream()
                .filter(NotionProperty.Title.class::isInstance)
                .map(NotionProperty::asText)
                .findFirst()
                .orElse("");
    }

    public String plainText() {
        return properties.values().stream()
                .map(NotionProperty::asText)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(" "));
    }
}
