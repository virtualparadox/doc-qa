package eu.virtualparadox.docqa.catalog.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.file.Path;

@Converter(autoApply = true)
public class PathConverter implements AttributeConverter<Path, String> {

    @Override
    public String convertToDatabaseColumn(Path attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public Path convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Path.of(dbData);
    }
}
