package eu.virtualparadox.docqa.catalog.entity;

import eu.virtualparadox.docqa.catalog.EDocumentStatus;
import eu.virtualparadox.docqa.catalog.converter.PathConverter;
import jakarta.persistence.*;
import lombok.*;

import java.nio.file.Path;
import java.time.Instant;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(length = 512, nullable = false)
    private String title;

    @Column(length = 128, nullable = false)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int chunks;

    @Column(name = "embed_model", length = 128, nullable = false)
    private String embedModel;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "blob_path", length = 1024, nullable = false)
    @Convert(converter = PathConverter.class)
    private Path blobPath;

    @Column(name = "status", length = 32, nullable = false)
    private EDocumentStatus status;

    @PrePersist
    void prePersist() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }

        if (embedModel == null) {
            embedModel = "unknown";
        }

        if (status == null) {
            status = EDocumentStatus.QUEUED;
        }
    }
}
