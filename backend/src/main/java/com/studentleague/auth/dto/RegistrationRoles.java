package com.studentleague.auth.dto;

public final class RegistrationRoles {

    public static final String PATTERN = "FAN|PLAYER|CAPTAIN|REFEREE";
    public static final String MESSAGE = "Допустимая роль: болельщик, игрок, капитан или судья";

    private RegistrationRoles() {
    }
}
