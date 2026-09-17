package com.sander.notionbot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class NotionClient {

    private static final Logger log = LoggerFactory.getLogger(NotionClient.class);

    static final URI DEFAULT_BASE_URI = URI.create("https://api.notion.com/v1/");
    static final String NOTION_VERSION = "2022-06-28";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String token;
    private final URI baseUri;

    public NotionClient(String token, ObjectMapper mapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), mapper, token, DEFAULT_BASE_URI);
    }

    NotionClient(HttpClient http, ObjectMapper mapper, String token, URI baseUri) {
        this.http = http;
        this.mapper = mapper;
        this.token = token;
        this.baseUri = baseUri;
    }

    /** Returns raw page objects from every result page, following {@code next_cursor} until exhausted. */
    public List<JsonNode> queryDatabase(String databaseId) throws IOException, InterruptedException {
        List<JsonNode> results = new ArrayList<>();
        String cursor = null;
        int requests = 0;
        do {
            ObjectNode body = mapper.createObjectNode().put("page_size", PAGE_SIZE);
            if (cursor != null) {
                body.put("start_cursor", cursor);
            }
            JsonNode response = post("databases/" + databaseId + "/query", body);
            response.path("results").forEach(results::add);
            requests++;
            cursor = response.path("has_more").asBoolean(false)
                    ? response.path("next_cursor").textValue()
                    : null;
        } while (cursor != null);

        log.debug("Fetched {} pages from database {} in {} request(s)", results.size(), databaseId, requests);
        return results;
    }

    private JsonNode post(String path, JsonNode body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Notion-Version", NOTION_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            // Notion allows ~3 requests/s; large databases can trip this while paginating.
            if (status == 429 && attempt < MAX_RATE_LIMIT_RETRIES) {
                long waitSeconds = response.headers().firstValueAsLong("Retry-After").orElse(1);
                log.warn("Rate limited by Notion on {}, retrying in {}s", path, waitSeconds);
                Thread.sleep(Duration.ofSeconds(waitSeconds));
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Notion API POST %s failed with HTTP %d: %s"
                        .formatted(path, status, response.body()));
            }
            return mapper.readTree(response.body());
        }
    }
}
