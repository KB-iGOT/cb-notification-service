package notification.example.notification_service.repository;

import notification.example.notification_service.entity.Notification;
import notification.example.notification_service.entity.Template;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends CassandraRepository<Notification, UUID> {
    @Query("SELECT * FROM notifications WHERE id = ?0 AND is_deleted = ?1 ALLOW FILTERING")
    Optional<Notification> findByIdAndIsDeleted(String id, Boolean isDeleted);
}
