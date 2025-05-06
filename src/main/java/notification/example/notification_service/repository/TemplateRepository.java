package notification.example.notification_service.repository;

import notification.example.notification_service.entity.Template;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateRepository extends CassandraRepository<Template, UUID> {
    @Query("SELECT * FROM templates WHERE id = ?0 AND is_deleted = ?1 ALLOW FILTERING")
    Optional<Template> findByIdAndIsDeleted(String id, Boolean isDeleted);
}
