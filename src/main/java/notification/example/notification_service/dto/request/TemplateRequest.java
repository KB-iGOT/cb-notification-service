package notification.example.notification_service.dto.request;

import lombok.Data;

@Data
public class TemplateRequest {
    private String messageTemplate;
    private String subjectTemplate;
}

