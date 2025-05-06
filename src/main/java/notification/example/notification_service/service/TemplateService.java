package notification.example.notification_service.service;


import notification.example.notification_service.dto.request.TemplateRequest;
import notification.example.notification_service.entity.Template;
import notification.example.notification_service.exception.TemplateNotFoundException;
import notification.example.notification_service.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class TemplateService {

    @Autowired
    private TemplateRepository templateRepository;

    public Template createTemplate(TemplateRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Template template = Template.builder()
                .id(String.valueOf(id))
                .messageTemplate(request.getMessageTemplate())
                .subjectTemplate(request.getSubjectTemplate())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return templateRepository.save(template);
    }


    public Template getTemplate(String templateId) {
        return templateRepository.findByIdAndIsDeleted(templateId, false).orElseThrow(() -> new TemplateNotFoundException("template not found by id " + templateId));
    }

}


