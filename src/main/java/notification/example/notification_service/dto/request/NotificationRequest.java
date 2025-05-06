package notification.example.notification_service.dto.request;

import lombok.Data;

@Data
public class NotificationRequest {
    private String type;
    private String category;
    private String source;
    private String template_id;
    private String role;
}
