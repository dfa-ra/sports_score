package com.studentleague.players;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredNameQueryTest {

    @Test
    void fullNameMatchesEitherOrderCaseAndExtraSpaces() {
        assertTrue(RegisteredNameQuery.matches("Илья", "Орлов", "Орлов Илья"));
        assertTrue(RegisteredNameQuery.matches("Илья", "Орлов", "Илья Орлов"));
        assertTrue(RegisteredNameQuery.matches("Илья", "Орлов", "  орлов   ИЛЬЯ  "));
        assertTrue(RegisteredNameQuery.matches("Илья", "Орлов", "ОРЛОВ"));
        assertTrue(RegisteredNameQuery.matches("Илья", "Орлов", "иль"));

        assertTrue(RegisteredNameQuery.matches("Толян", "Игроков", "Игроков Толян"));
        assertTrue(RegisteredNameQuery.matches("Толян", "Игроков", "Толян Игроков"));
        assertTrue(RegisteredNameQuery.matches("Толян", "Игроков", "  игроков    толян "));
        assertTrue(RegisteredNameQuery.matches("Толян", "Игроков", "ТОЛЯН"));
        assertTrue(RegisteredNameQuery.matches("Толян", "Игроков", "Игрок"));
    }

    @Test
    void unknownNameAndShirtNicknameDoNotMatch() {
        assertFalse(RegisteredNameQuery.matches("Илья", "Орлов", "Хрюков"));
        assertFalse(RegisteredNameQuery.matches("Толян", "Игроков", "Неттакого"));
        assertFalse(RegisteredNameQuery.matches("Пётр", "Иванов", "Vanya"));
        assertFalse(RegisteredNameQuery.matches("Илья", "Орлов", "И"));
        assertFalse(RegisteredNameQuery.matches("Илья", "Орлов", "   "));
    }
}
