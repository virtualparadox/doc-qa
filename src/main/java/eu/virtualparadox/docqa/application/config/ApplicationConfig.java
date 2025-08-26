package eu.virtualparadox.docqa.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "docqa")
@Getter @Setter
public class ApplicationConfig {

    private Path root;
    private Path index;
    private Path db;
    private Path blob;
    private Path models;

    @PostConstruct
    public void ensureFolders() throws IOException {
        if (root != null) Files.createDirectories(root);
        if (index != null) Files.createDirectories(index);
        if (blob != null) Files.createDirectories(blob);
        if (models != null) Files.createDirectories(models);
        if (db != null) {
            Path dbDir = db.getParent();
            if (dbDir != null) Files.createDirectories(dbDir);
        }
    }
}
