package com.chanakanlabs.theparlor.branches.internal.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DayOfWeekConverter implements AttributeConverter<java.time.DayOfWeek, Short> {
  @Override
  public Short convertToDatabaseColumn(java.time.DayOfWeek day) {
    return day == null ? null : (short) day.getValue();
  }

  @Override
  public java.time.DayOfWeek convertToEntityAttribute(Short value) {
    return value == null ? null : java.time.DayOfWeek.of(value);
  }
}
