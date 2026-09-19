package com.sander.notionbot.slack;

import com.sander.notionbot.notion.NotionService;
import com.sander.notionbot.slack.handlers.AppMentionHandler;
import com.sander.notionbot.slack.handlers.NotionCommandHandler;
import com.sander.notionbot.slack.handlers.StrafferCommandHandler;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.model.event.AppMentionEvent;

import java.util.concurrent.ExecutorService;

public final class SlackApp {

    private SlackApp() {
    }

    public static App create(String botToken, NotionService notionService, ExecutorService executor) {
        App app = new App(AppConfig.builder().singleTeamBotToken(botToken).build());
        app.command("/notion", new NotionCommandHandler(notionService, executor));
        app.command("/straffer", new StrafferCommandHandler(notionService, executor));
        app.event(AppMentionEvent.class, new AppMentionHandler(notionService, executor));
        return app;
    }
}
