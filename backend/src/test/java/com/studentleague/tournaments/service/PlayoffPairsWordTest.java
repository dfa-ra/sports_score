package com.studentleague.tournaments.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayoffPairsWordTest {

    @Test
    void pairCountDeclension() {
        assertEquals("пару", PlayoffService.pairsWord(1));
        assertEquals("пары", PlayoffService.pairsWord(2));
        assertEquals("пары", PlayoffService.pairsWord(4));
        assertEquals("пар", PlayoffService.pairsWord(5));
        assertEquals("пар", PlayoffService.pairsWord(8));
        assertEquals("пар", PlayoffService.pairsWord(11));
        assertEquals("пар", PlayoffService.pairsWord(12));
        assertEquals("пар", PlayoffService.pairsWord(14));
        assertEquals("пару", PlayoffService.pairsWord(21));
        assertEquals("пары", PlayoffService.pairsWord(22));
        assertEquals("пары", PlayoffService.pairsWord(24));

        assertEquals("Нужно назначить 1 пару из вышедших команд", PlayoffService.pairsRequiredMessage(1));
        assertEquals("Нужно назначить 2 пары из вышедших команд", PlayoffService.pairsRequiredMessage(2));
        assertEquals("Нужно назначить 4 пары из вышедших команд", PlayoffService.pairsRequiredMessage(4));
        assertEquals("Нужно назначить 5 пар из вышедших команд", PlayoffService.pairsRequiredMessage(5));
        assertEquals("Нужно назначить 8 пар из вышедших команд", PlayoffService.pairsRequiredMessage(8));
        assertEquals("Нужно назначить 11 пар из вышедших команд", PlayoffService.pairsRequiredMessage(11));
        assertEquals("Нужно назначить 21 пару из вышедших команд", PlayoffService.pairsRequiredMessage(21));
    }
}
