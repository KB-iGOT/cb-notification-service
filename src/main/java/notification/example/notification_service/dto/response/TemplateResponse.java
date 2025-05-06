package notification.example.notification_service.dto.response;

import java.util.UUID;

public class TemplateResponse {
    private String status;
    private String message;
    private UUID templateId;

    public TemplateResponse(String status, String message, UUID templateId) {
        this.status = status;
        this.message = message;
        this.templateId = templateId;
    }
}