package com.sander.notionbot.notion.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PenaltyBalanceTest {

    @ParameterizedTest(name = "{0} ikke innløst -> {1} anbefalt")
    @CsvSource({
            "0, 0",
            "1, 0",
            "2, 1",
            "5, 2",
            "11, 5",
            "12, 6",
            "13, 6",
            "25, 6",
    })
    void recommendedIsHalfRoundedDownCappedAtSix(int unredeemed, int expected) {
        assertEquals(expected, new PenaltyBalance("Navn", unredeemed).recommended());
    }
}
