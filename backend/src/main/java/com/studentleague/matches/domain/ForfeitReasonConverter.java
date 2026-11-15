package com.studentleague.matches.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ForfeitReasonConverter implements AttributeConverter<ForfeitReason, String> {

    @Override
    public String convertToDatabaseColumn(ForfeitReason reason) {
        return reason == null ? null : reason.value();
    }

    @Override
    public ForfeitReason convertToEntityAttribute(String raw) {
        return ForfeitReason.from(raw);
    }
}
