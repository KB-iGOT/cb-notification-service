package notification.example.notification_service.controller;

import notification.example.notification_service.dto.request.NotificationRequest;
import notification.example.notification_service.entity.Notification;
import notification.example.notification_service.service.NotificationService;
import notification.example.notification_service.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Notification> createNotification(
            @RequestBody NotificationRequest request,
            @RequestHeader(Constants.X_AUTH_TOKEN) String userToken) {
        Notification notification = notificationService.createNotification(request, userToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<Notification> getById(@PathVariable("notificationId") String notificationId) {
        Notification notification = notificationService.getNotificationById(notificationId);
        return ResponseEntity.ok(notification);
    }
}
