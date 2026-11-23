package mn.diploma.routeplanner.auth;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import mn.diploma.routeplanner.common.TransportMode;

/** Тээврийн хэрэгслийн олонлогийг "WALKING,PUBLIC_TRANSPORT" хэлбэрээр хадгална. */
@Converter
public class TransportModesConverter implements AttributeConverter<Set<TransportMode>, String> {

    @Override
    public String convertToDatabaseColumn(Set<TransportMode> modes) {
        return modes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<TransportMode> convertToEntityAttribute(String value) {
        Set<TransportMode> modes = EnumSet.noneOf(TransportMode.class);
        if (value != null && !value.isBlank()) {
            Arrays.stream(value.split(",")).map(String::trim).map(TransportMode::valueOf).forEach(modes::add);
        }
        return modes;
    }
}
