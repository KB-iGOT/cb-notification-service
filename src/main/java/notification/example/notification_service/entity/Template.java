package notification.example.notification_service.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@Table("templates")
public class Template {
    @PrimaryKey
    private String id;

    @Column("message_template")
    private String messageTemplate;

    @Column("subject_template")
    private String subjectTemplate;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("is_deleted")
    private boolean isDeleted;

}

