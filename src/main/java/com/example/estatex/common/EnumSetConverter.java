package com.example.estatex.common;

import jakarta.persistence.AttributeConverter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persists a small set of enum values as a comma-separated VARCHAR ("PREA,NEGOTIATOR"). Used for
 * per-row flag sets (roles held in one organisation, divisions enabled) that are always read
 * whole with their row, so a join table would add cost without adding anything. Unknown names
 * (e.g. a value removed from the enum) are skipped on read rather than failing the whole row.
 */
public abstract class EnumSetConverter<E extends Enum<E>> implements AttributeConverter<Set<E>, String> {
    private final Class<E> type;

    protected EnumSetConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(Set<E> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<E> convertToEntityAttribute(String value) {
        EnumSet<E> result = EnumSet.noneOf(type);
        if (value == null || value.isBlank()) {
            return result;
        }
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(name -> {
                    try {
                        result.add(Enum.valueOf(type, name));
                    } catch (IllegalArgumentException ignored) {
                        // value no longer exists in the enum; drop it
                    }
                });
        return result;
    }
}
