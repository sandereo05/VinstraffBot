package com.sander.notionbot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.sander.notionbot.cache.NotionCache;
import com.sander.notionbot.notion.model.NotionPage;
import com.sander.notionbot.notion.model.NotionProperty;
import com.sander.notionbot.notion.model.PenaltyBalance;

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

    static final String UNREDEEMED_PROPERTY = "Ikke innløste vinstraffer";

    private final NotionClient client;
    private final NotionCache cache;
    private final String databaseId;
    private final String membersDatabaseId;

    public NotionService(NotionClient client, NotionCache cache, String databaseId, String membersDatabaseId) {
        this.client = client;
        this.cache = cache;
        this.databaseId = databaseId;
        this.membersDatabaseId = membersDatabaseId;
    }

    public List<NotionPage> allPages() {
        return cache.getOrLoad(databaseId, () -> loadPages(databaseId));
    }

    public List<NotionPage> latestPages(int limit) {
        return allPages().stream()
                .sorted(Comparator.comparing(NotionPage::createdTime).reversed())
                .limit(limit)
                .toList();
    }

    /** Members with at least one unredeemed penalty, most first. */
    public List<PenaltyBalance> penaltyBalances() {
        return cache.getOrLoad(membersDatabaseId, () -> loadPages(membersDatabaseId)).stream()
                .map(NotionService::toBalance)
                .filter(balance -> balance.unredeemed() > 0)
                .sorted(Comparator.comparingInt(PenaltyBalance::unredeemed).reversed()
                        .thenComparing(PenaltyBalance::name))
                .toList();
    }

    // Fails loudly rather than defaulting to 0: a renamed Notion property would otherwise
    // make everyone look debt-free.
    static PenaltyBalance toBalance(NotionPage member) {
        NotionProperty property = member.properties().get(UNREDEEMED_PROPERTY);
        if (!(property instanceof NotionProperty.Numeric numeric)) {
            throw new IllegalStateException("Expected numeric property '%s' on member '%s', got %s"
                    .formatted(UNREDEEMED_PROPERTY, member.title(), property));
        }
        int unredeemed = numeric.value() == null ? 0 : numeric.value().intValue();
        return new PenaltyBalance(member.title(), unredeemed);
    }

    private List<NotionPage> loadPages(String databaseId) {
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
            case "created_time", "last_edited_time" -> new NotionProperty.DateRange(value.textValue(), null);
            case "formula", "rollup" -> toComputedProperty(type, value);
            default -> new NotionProperty.Unsupported(type);
        };
    }

    /** Formula and rollup wrap their result as {@code {"type": "number", "number": 4}}. */
    private static NotionProperty toComputedProperty(String type, JsonNode value) {
        String resultType = value.path("type").asText();
        JsonNode result = value.path(resultType);
        return switch (resultType) {
            case "number" -> new NotionProperty.Numeric(result.isNumber() ? result.decimalValue() : null);
            case "boolean" -> new NotionProperty.Checkbox(result.asBoolean(false));
            case "string" -> new NotionProperty.RichText(result.textValue());
            case "date" -> new NotionProperty.DateRange(result.path("start").textValue(), result.path("end").textValue());
            default -> new NotionProperty.Unsupported(type + ":" + resultType);
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
