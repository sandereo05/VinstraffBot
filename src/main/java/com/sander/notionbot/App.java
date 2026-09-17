package com.sander.notionbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sander.notionbot.cache.NotionCache;
import com.sander.notionbot.config.Config;
import com.sander.notionbot.notion.NotionClient;
import com.sander.notionbot.notion.NotionService;
import com.sander.notionbot.slack.SlackApp;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) throws Exception {
        Config config;
        try {
            config = Config.load();
        } catch (IllegalStateException e) {
            log.error("Configuration error: {}", e.getMessage());
            System.exit(1);
            return;
        }
        log.info("Starting with {}", config);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        NotionClient notionClient = new NotionClient(config.notionToken(), new ObjectMapper());
        NotionService notionService = new NotionService(
                notionClient, new NotionCache(config.cacheTtl()), config.notionDatabaseId());

        SocketModeApp socketModeApp = new SocketModeApp(
                config.slackAppToken(),
                SlackApp.create(config.slackBotToken(), notionService, executor));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            try {
                socketModeApp.stop();
            } catch (Exception e) {
                log.warn("Error while stopping Socket Mode app", e);
            }
            executor.close();
        }));

        socketModeApp.start();
    }
}
