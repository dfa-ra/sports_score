package com.studentleague.matches.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ForfeitSideConverter implements AttributeConverter<ForfeitSide, String> {

    @Override
    public String convertToDatabaseColumn(ForfeitSide side) {
        return side == null ? ForfeitSide.NONE.value() : side.value();
    }

    @Override
    public ForfeitSide convertToEntityAttribute(String raw) {
        ForfeitSide side = ForfeitSide.from(raw);
        return side == null ? ForfeitSide.NONE : side;
    }
}
