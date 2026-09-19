package com.sander.notionbot.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public record Config(
        String notionToken,
        String notionDatabaseId,
        String notionMembersDatabaseId,
        String slackBotToken,
        String slackAppToken,
        Duration cacheTtl) {

    static final List<String> REQUIRED_VARIABLES =
            List.of("NOTION_TOKEN", "NOTION_DATABASE_ID", "NOTION_MEMBERS_DATABASE_ID",
                    "SLACK_BOT_TOKEN", "SLACK_APP_TOKEN");
    static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    /** Real environment variables take precedence over values in {@code .env}. */
    public static Config load() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        return from(dotenv::get);
    }

    static Config from(Function<String, String> lookup) {
        List<String> missing = REQUIRED_VARIABLES.stream()
                .filter(name -> isBlank(lookup.apply(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variable(s): " + String.join(", ", missing)
                            + ". Set them in the environment or in a .env file (see .env.example).");
        }
        return new Config(
                lookup.apply("NOTION_TOKEN").trim(),
                lookup.apply("NOTION_DATABASE_ID").trim(),
                lookup.apply("NOTION_MEMBERS_DATABASE_ID").trim(),
                lookup.apply("SLACK_BOT_TOKEN").trim(),
                lookup.apply("SLACK_APP_TOKEN").trim(),
                parseCacheTtl(lookup.apply("NOTION_CACHE_TTL_MINUTES")));
    }

    private static Duration parseCacheTtl(String minutes) {
        if (isBlank(minutes)) {
            return DEFAULT_CACHE_TTL;
        }
        try {
            long value = Long.parseLong(minutes.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return Duration.ofMinutes(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "NOTION_CACHE_TTL_MINUTES must be a positive integer, got: '" + minutes + "'");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "Config[notionDatabaseId=%s, notionMembersDatabaseId=%s, cacheTtl=%s]"
                .formatted(notionDatabaseId, notionMembersDatabaseId, cacheTtl);
    }
}
