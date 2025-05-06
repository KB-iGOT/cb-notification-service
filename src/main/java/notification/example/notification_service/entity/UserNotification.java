package notification.example.notification_service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;
import java.time.Instant;

@Table("user_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {

    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "notification_id", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private String notificationId;

    @Column("read")
    private boolean read;

    @Column("read_at")
    private Instant readAt;
}