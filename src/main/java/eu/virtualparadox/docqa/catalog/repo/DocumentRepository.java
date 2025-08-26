package eu.virtualparadox.docqa.catalog.repo;

import eu.virtualparadox.docqa.catalog.EDocumentStatus;
import eu.virtualparadox.docqa.catalog.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    @Modifying
    @Query("update DocumentEntity d set d.status = :status where d.id = :id")
    int updateStatus(@Param("id") String id, @Param("status") EDocumentStatus status);

    @Modifying
    @Query("update DocumentEntity d set d.chunks = :chunks, d.status = :status where d.id = :id")
    int updateChunksAndStatus(@Param("id") String id,
                              @Param("chunks") int chunks,
                              @Param("status") EDocumentStatus status);
}
