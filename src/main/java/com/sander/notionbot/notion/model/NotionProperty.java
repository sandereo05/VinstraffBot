package com.sander.notionbot.notion.model;

import java.math.BigDecimal;
import java.util.List;

public sealed interface NotionProperty {

    record Title(String text) implements NotionProperty {}

    record RichText(String text) implements NotionProperty {}

    record Numeric(BigDecimal value) implements NotionProperty {}

    /** Covers both {@code select} and {@code status}, which share the same shape. */
    record Select(String name) implements NotionProperty {}

    record MultiSelect(List<String> names) implements NotionProperty {}

    record Checkbox(boolean checked) implements NotionProperty {}

    /** Kept as strings because Notion mixes date-only and date-time values. */
    record DateRange(String start, String end) implements NotionProperty {}

    record Url(String url) implements NotionProperty {}

    record People(List<String> names) implements NotionProperty {}

    record Unsupported(String type) implements NotionProperty {}

    default String asText() {
        return switch (this) {
            case Title t -> orEmpty(t.text());
            case RichText r -> orEmpty(r.text());
            case Numeric n -> n.value() == null ? "" : n.value().toPlainString();
            case Select s -> orEmpty(s.name());
            case MultiSelect m -> String.join(", ", m.names());
            case Checkbox c -> c.checked() ? "✓" : "✗";
            case DateRange d -> d.end() == null ? orEmpty(d.start()) : orEmpty(d.start()) + " → " + d.end();
            case Url u -> orEmpty(u.url());
            case People p -> String.join(", ", p.names());
            case Unsupported u -> "";
        };
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
