package com.igot.cb.userNotificationSetting.repository;

import com.igot.cb.userNotificationSetting.entity.NotificationSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSettingEntity, String> {

    List<NotificationSettingEntity> findByUserId(String userId);

    List<NotificationSettingEntity> findByUserIdAndIsDeletedFalse(String userId);

    Optional<NotificationSettingEntity> findByUserIdAndNotificationTypeAndIsDeletedFalse(String userId, String notificationType);

}
