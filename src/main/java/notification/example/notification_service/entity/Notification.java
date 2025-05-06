package notification.example.notification_service.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@Table("notifications")
public class Notification {
    @PrimaryKey
    private String id;

    @Column("type")
    private String type;

    @Column("category")
    private String category;

    @Column("source")
    private String source;

    @Column("role")
    private String role;

    @Column("template_id")
    private String templateId;

    @Column("created_at")
    private Instant createdAt;

    @Column("is_deleted")
    private boolean isDeleted;
}
