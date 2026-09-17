package com.sander.notionbot.slack.handlers;

import com.sander.notionbot.notion.NotionService;
import com.sander.notionbot.notion.model.NotionPage;
import com.sander.notionbot.notion.model.NotionProperty;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class NotionCommandHandler implements SlashCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(NotionCommandHandler.class);

    static final int ROW_LIMIT = 5;

    private final NotionService notionService;
    private final ExecutorService executor;

    public NotionCommandHandler(NotionService notionService, ExecutorService executor) {
        this.notionService = notionService;
        this.executor = executor;
    }

    @Override
    public Response apply(SlashCommandRequest request, SlashCommandContext ctx) {
        // Slack requires an ack within 3s, but a cold cache means crawling all of Notion,
        // so the actual answer is sent later through response_url.
        executor.submit(() -> respond(ctx));
        return ctx.ack();
    }

    private void respond(SlashCommandContext ctx) {
        String text;
        try {
            text = "*Siste %d rader fra Notion:*\n%s".formatted(ROW_LIMIT, formatPages(notionService.latestPages(ROW_LIMIT)));
        } catch (RuntimeException e) {
            log.error("Failed to fetch pages from Notion", e);
            text = ":warning: Klarte ikke å hente data fra Notion. Sjekk loggene.";
        }

        String message = text;
        try {
            ctx.respond(r -> r.responseType("ephemeral").text(message));
        } catch (IOException e) {
            log.error("Failed to send /notion response", e);
        }
    }

    static String formatPages(List<NotionPage> pages) {
        if (pages.isEmpty()) {
            return "_Ingen rader funnet._";
        }
        return pages.stream()
                .map(NotionCommandHandler::formatPage)
                .collect(Collectors.joining("\n"));
    }

    private static String formatPage(NotionPage page) {
        String title = page.title().isBlank() ? "(uten tittel)" : page.title();
        String line = "• <%s|%s>".formatted(page.url(), escape(title));

        String details = page.properties().entrySet().stream()
                .filter(entry -> !(entry.getValue() instanceof NotionProperty.Title))
                .filter(entry -> !entry.getValue().asText().isBlank())
                .map(entry -> "*%s:* %s".formatted(escape(entry.getKey()), escape(entry.getValue().asText())))
                .collect(Collectors.joining("  ·  "));

        return details.isEmpty() ? line : line + "\n      " + details;
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
