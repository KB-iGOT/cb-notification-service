package com.igot.cb.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.igot.cb.notification.entity.NotificationSettingEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSettingEntity, String> {

    List<NotificationSettingEntity> findByUserIdAndIsDeletedFalse(String userId);

    Optional<NotificationSettingEntity> findByUserIdAndNotificationTypeAndIsDeletedFalse(String userId, String notificationType);

    List<NotificationSettingEntity> findByUserIdInAndNotificationTypeInAndIsDeletedFalse(List<String> userIds, List<String> notificationTypes);
}
