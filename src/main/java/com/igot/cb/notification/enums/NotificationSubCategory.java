package com.igot.cb.notification.enums;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.time.Duration;

@Getter
public enum NotificationSubCategory {
    CONTENT_REVIEW_REQUEST(false),
    CONTENT_PUBLISHED(false),
    CONTENT_SPV_PUBLISHED(false),
    CONTENT_REJECTED(false),
    CONTENT_EDITED(false),
    LIKED_POST(true) {
        @Override
        public String messageTemplate() {
            return "{count} users liked your post.";
        }

        @Override
        public Duration clubWindow() {
            return Duration.ofMinutes(15);
        }

        @Override
        public String clubKey(JsonNode data) {
            return data.get("discussionId").asText();
        }
    },
    LIKED_COMMENT(true) {
        @Override
        public String messageTemplate() {
            return "{count} users commented on your post.";
        }

        @Override
        public Duration clubWindow() {
            return Duration.ofMinutes(15);
        }

        @Override
        public String clubKey(JsonNode data) {
            return data.get("discussionId").asText();
        }
    },
    REPLIED_POST(true) {
        @Override
        public String messageTemplate() {
            return "You have {count} replies on your comment.";
        }

        @Override
        public Duration clubWindow() {
            return Duration.ofMinutes(15);
        }

        @Override
        public String clubKey(JsonNode data) {
            return data.get("discussionId").asText();
        }
    },
    POST_COMMENT(false),
    REPLIED_COMMENT(true){
        @Override
        public String messageTemplate(){
            return "You have {count} replies on your comment.";
        }
        @Override
        public Duration clubWindow() {
            return Duration.ofMinutes(15);
        }

        @Override
        public String clubKey(JsonNode data) {
            return data.get("discussionId").asText();
        }

    },
    SEND_CONNECTION_REQUEST(true) {
        @Override
        public String messageTemplate() {
            return "You received {count} new connection requests.";
        }

        @Override
        public Duration clubWindow() {
            return Duration.ofMinutes(60);
        }

        @Override
        public String clubKey(JsonNode data) {
            return data.get("user_id").asText();
        }
    },
    ACCEPTED_CONNECTION_REQUEST(true) {
        @Override
        public String messageTemplate() {
            return "{count} users accepted your connection request.";
        }

        @Override
        public Duration clubWindow() {
            return Duration.ofMinutes(60);
        }

        @Override
        public String clubKey(JsonNode data) {
            return data.get("user_id").asText();
        }
    },
    REJECTED_CONNECTION_REQUEST(false),
    PROFILE_VERIFICATION(false),
    USER_TRANSFER(false),
    CONTENT_SHARE(false),
    TAGGED_COMMENT(false),
    TAGGED_POST(false);

    private boolean shouldClub;

    public String messageTemplate() {
        throw new UnsupportedOperationException();
    }

    public Duration clubWindow() {
        throw new UnsupportedOperationException();
    }

    public String clubKey(JsonNode data) {
        throw new UnsupportedOperationException();
    }

    NotificationSubCategory(boolean shouldClub) {
        this.shouldClub = shouldClub;
    }
}