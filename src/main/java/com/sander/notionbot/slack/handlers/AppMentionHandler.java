package com.sander.notionbot.slack.handlers;

import com.sander.notionbot.notion.NotionService;
import com.sander.notionbot.notion.model.NotionPage;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.handler.BoltEventHandler;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.event.AppMentionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

public class AppMentionHandler implements BoltEventHandler<AppMentionEvent> {

    private static final Logger log = LoggerFactory.getLogger(AppMentionHandler.class);

    private static final Pattern USER_MENTION = Pattern.compile("<@[A-Z0-9]+>");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MIN_KEYWORD_LENGTH = 3;
    static final int MAX_MATCHES = 5;

    private final NotionService notionService;
    private final ExecutorService executor;

    public AppMentionHandler(NotionService notionService, ExecutorService executor) {
        this.notionService = notionService;
        this.executor = executor;
    }

    @Override
    public Response apply(EventsApiPayload<AppMentionEvent> payload, EventContext ctx) {
        AppMentionEvent event = payload.getEvent();
        executor.submit(() -> reply(event, ctx));
        return ctx.ack();
    }

    private void reply(AppMentionEvent event, EventContext ctx) {
        String question = USER_MENTION.matcher(event.getText()).replaceAll("").trim();

        String text;
        try {
            // TODO(RAG): replace keyword matching with retrieval over embedded Notion content
            //  and let an LLM compose the answer from the retrieved chunks.
            List<NotionPage> matches = findMatches(question, notionService.allPages());
            text = matches.isEmpty()
                    ? "Fant ingen rader som matcher «%s».".formatted(NotionCommandHandler.escape(question))
                    : "*Rader som matcher:*\n" + NotionCommandHandler.formatPages(matches);
        } catch (RuntimeException e) {
            log.error("Failed to answer mention", e);
            text = ":warning: Klarte ikke å hente data fra Notion. Sjekk loggene.";
        }

        String threadTs = event.getThreadTs() != null ? event.getThreadTs() : event.getTs();
        String message = text;
        try {
            ChatPostMessageResponse response = ctx.client().chatPostMessage(r -> r
                    .channel(event.getChannel())
                    .threadTs(threadTs)
                    .text(message));
            if (!response.isOk()) {
                log.warn("chat.postMessage failed: {}", response.getError());
            }
        } catch (IOException | SlackApiException e) {
            log.error("Failed to post mention reply", e);
        }
    }

    /** Naive placeholder: ranks pages by how many distinct question keywords they contain. */
    static List<NotionPage> findMatches(String question, List<NotionPage> pages) {
        List<String> keywords = Arrays.stream(NON_WORD.split(question.toLowerCase(Locale.ROOT)))
                .filter(word -> word.length() >= MIN_KEYWORD_LENGTH)
                .distinct()
                .toList();
        if (keywords.isEmpty()) {
            return List.of();
        }

        record Scored(NotionPage page, long score) {}

        return pages.stream()
                .map(page -> {
                    String haystack = page.plainText().toLowerCase(Locale.ROOT);
                    return new Scored(page, keywords.stream().filter(haystack::contains).count());
                })
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingLong(Scored::score).reversed())
                .limit(MAX_MATCHES)
                .map(Scored::page)
                .toList();
    }
}
