package notification.example.notification_service.repository;
import notification.example.notification_service.entity.UserNotification;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserNotificationRepository  extends  CassandraRepository<UserNotification,UUID> {

}


