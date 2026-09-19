package com.sander.notionbot.slack.handlers;

import com.sander.notionbot.notion.NotionService;
import com.sander.notionbot.notion.model.PenaltyBalance;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class StrafferCommandHandler implements SlashCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StrafferCommandHandler.class);

    private static final String NAME_HEADER = "Navn";
    private static final String UNREDEEMED_HEADER = "Ikke innløst";
    private static final String RECOMMENDED_HEADER = "Anbefalt";

    private final NotionService notionService;
    private final ExecutorService executor;

    public StrafferCommandHandler(NotionService notionService, ExecutorService executor) {
        this.notionService = notionService;
        this.executor = executor;
    }

    @Override
    public Response apply(SlashCommandRequest request, SlashCommandContext ctx) {
        executor.submit(() -> respond(ctx));
        return ctx.ack();
    }

    private void respond(SlashCommandContext ctx) {
        String text;
        String responseType = "in_channel";
        try {
            text = format(notionService.penaltyBalances());
        } catch (RuntimeException e) {
            log.error("Failed to fetch penalty balances from Notion", e);
            text = ":warning: Klarte ikke å hente vinstraffer fra Notion. Sjekk loggene.";
            responseType = "ephemeral";
        }

        String message = text;
        String type = responseType;
        try {
            ctx.respond(r -> r.responseType(type).text(message));
        } catch (IOException e) {
            log.error("Failed to send /straffer response", e);
        }
    }

    static String format(List<PenaltyBalance> balances) {
        if (balances.isEmpty()) {
            return "Ingen ikke-innløste vinstraffer :tada:";
        }

        int total = balances.stream().mapToInt(PenaltyBalance::unredeemed).sum();
        int nameWidth = Math.max(NAME_HEADER.length(),
                balances.stream().mapToInt(balance -> balance.name().length()).max().orElse(0));
        String rowFormat = "%-" + nameWidth + "s  %" + UNREDEEMED_HEADER.length() + "s  %" + RECOMMENDED_HEADER.length() + "s%n";

        StringBuilder table = new StringBuilder()
                .append(rowFormat.formatted(NAME_HEADER, UNREDEEMED_HEADER, RECOMMENDED_HEADER));
        for (PenaltyBalance balance : balances) {
            table.append(rowFormat.formatted(balance.name(), balance.unredeemed(), balance.recommended()));
        }

        return """
                *Ikke innløste vinstraffer* — %d personer, %d totalt
                ```
                %s```
                _Anbefalt = halvparten, maks %d._"""
                .formatted(balances.size(), total, NotionCommandHandler.escape(table.toString()),
                        PenaltyBalance.MAX_RECOMMENDED);
    }
}
