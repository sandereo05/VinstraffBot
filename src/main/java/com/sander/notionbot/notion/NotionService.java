package com.sander.notionbot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.sander.notionbot.cache.NotionCache;
import com.sander.notionbot.notion.model.NotionPage;
import com.sander.notionbot.notion.model.NotionProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class NotionService {

    private final NotionClient client;
    private final NotionCache cache;
    private final String databaseId;

    public NotionService(NotionClient client, NotionCache cache, String databaseId) {
        this.client = client;
        this.cache = cache;
        this.databaseId = databaseId;
    }

    public List<NotionPage> allPages() {
        return cache.getOrLoad(databaseId, this::loadPages);
    }

    public List<NotionPage> latestPages(int limit) {
        return allPages().stream()
                .sorted(Comparator.comparing(NotionPage::createdTime).reversed())
                .limit(limit)
                .toList();
    }

    private List<NotionPage> loadPages() {
        try {
            // TODO(RAG): after crawling, chunk and embed page content here (or in a listener)
            //  so the vector index is refreshed together with the cache.
            return client.queryDatabase(databaseId).stream()
                    .map(NotionService::toPage)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying Notion", e);
        }
    }

    static NotionPage toPage(JsonNode json) {
        Map<String, NotionProperty> properties = new LinkedHashMap<>();
        json.path("properties").properties()
                .forEach(entry -> properties.put(entry.getKey(), toProperty(entry.getValue())));
        return new NotionPage(
                json.path("id").asText(),
                json.path("url").asText(),
                Instant.parse(json.path("created_time").asText()),
                Instant.parse(json.path("last_edited_time").asText()),
                properties);
    }

    static NotionProperty toProperty(JsonNode json) {
        String type = json.path("type").asText();
        JsonNode value = json.path(type);
        return switch (type) {
            case "title" -> new NotionProperty.Title(plainText(value));
            case "rich_text" -> new NotionProperty.RichText(plainText(value));
            case "email", "phone_number" -> new NotionProperty.RichText(value.textValue());
            case "number" -> new NotionProperty.Numeric(value.isNumber() ? value.decimalValue() : null);
            case "select", "status" -> new NotionProperty.Select(value.path("name").textValue());
            case "multi_select" -> new NotionProperty.MultiSelect(names(value));
            case "checkbox" -> new NotionProperty.Checkbox(value.asBoolean(false));
            case "date" -> new NotionProperty.DateRange(value.path("start").textValue(), value.path("end").textValue());
            case "url" -> new NotionProperty.Url(value.textValue());
            case "people" -> new NotionProperty.People(names(value));
            default -> new NotionProperty.Unsupported(type);
        };
    }

    private static String plainText(JsonNode richTextArray) {
        return StreamSupport.stream(richTextArray.spliterator(), false)
                .map(segment -> segment.path("plain_text").asText())
                .collect(Collectors.joining());
    }

    private static List<String> names(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(item -> item.path("name").textValue())
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }
}
