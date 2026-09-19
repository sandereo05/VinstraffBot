package com.sander.notionbot.notion.model;

public record PenaltyBalance(String name, int unredeemed) {

    public static final int MAX_RECOMMENDED = 6;

    /** How many penalties the person should bring next time: half, rounded down, capped. */
    public int recommended() {
        return Math.min(MAX_RECOMMENDED, unredeemed / 2);
    }
}
