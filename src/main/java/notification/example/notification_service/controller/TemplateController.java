package notification.example.notification_service.controller;

import notification.example.notification_service.dto.request.TemplateRequest;
import notification.example.notification_service.entity.Template;
import notification.example.notification_service.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    @Autowired
    private TemplateService templateService;

    @PostMapping
    public ResponseEntity<Template> create(@RequestBody TemplateRequest request) {
        Template template = templateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(template);
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<Template> getById(@PathVariable("templateId") String templateId) {
        Template template = templateService.getTemplate(templateId);
        return ResponseEntity.ok(template);
    }

}




