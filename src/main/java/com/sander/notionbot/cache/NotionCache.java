package com.sander.notionbot.cache;

import com.sander.notionbot.notion.model.NotionPage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class NotionCache {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private record Entry(List<NotionPage> pages, Instant loadedAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public NotionCache() {
        this(DEFAULT_TTL);
    }

    public NotionCache(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    NotionCache(Duration ttl, Clock clock) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive, got " + ttl);
        }
        this.ttl = ttl;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<NotionPage> getOrLoad(String key, Supplier<List<NotionPage>> loader) {
        Entry entry = entries.get(key);
        if (entry != null && !isExpired(entry)) {
            return entry.pages();
        }
        // Deliberately not using compute(): the loader does slow HTTP calls, and holding the map's
        // bin lock during that would block other keys and pin virtual threads. Two concurrent misses
        // may both load, which is acceptable.
        List<NotionPage> pages = List.copyOf(loader.get());
        entries.put(key, new Entry(pages, clock.instant()));
        return pages;
    }

    public void invalidate(String key) {
        entries.remove(key);
    }

    public void invalidateAll() {
        entries.clear();
    }

    public Duration ttl() {
        return ttl;
    }

    private boolean isExpired(Entry entry) {
        return !clock.instant().isBefore(entry.loadedAt().plus(ttl));
    }
}
