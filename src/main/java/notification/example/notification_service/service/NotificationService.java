package notification.example.notification_service.service;

import notification.example.notification_service.dto.request.NotificationRequest;
import notification.example.notification_service.entity.Notification;
import notification.example.notification_service.entity.UserNotification;
import notification.example.notification_service.exception.NotificationNotFoundException;
import notification.example.notification_service.repository.NotificationRepository;
import notification.example.notification_service.repository.UserNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserNotificationRepository userNotificationRepository;


    public Notification createNotification(NotificationRequest request, String token) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .type(request.getType())
                .category(request.getCategory())
                .source(request.getSource())
                .templateId(request.getTemplate_id())
                .role(request.getRole())
                .createdAt(Instant.now())
                .build();

        notificationRepository.save(notification);

        // Parse or use static user ID (replace this with actual logic)
        String userId = "1232";

        UserNotification userNotification = new UserNotification(
                userId,
                notification.getId(),
                false,
                null
        );

        userNotificationRepository.save(userNotification);

        return notification;
    }

    public Notification getNotificationById(String notificationId) {
        return notificationRepository.findByIdAndIsDeleted(notificationId, false).orElseThrow(() -> new NotificationNotFoundException("notification not found by id " + notificationId));
    }
}
