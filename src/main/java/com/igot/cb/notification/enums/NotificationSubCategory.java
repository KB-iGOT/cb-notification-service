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
            return "You have {count} liked your reply.";
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
    SEND_CONNECTION_REQUEST(false),
    ACCEPTED_CONNECTION_REQUEST(false),
    REJECTED_CONNECTION_REQUEST(false),
    PROFILE_VERIFICATION(false),
    USER_TRANSFER(false),
    CONTENT_SHARE(false),
    TAGGED_COMMENT(false),
    TAGGED_POST(false),
    EVENT_PUBLISHED(false),
    EVENT_ENROLLED(false),
    COURSE_PUBLISHED(false),
    PROGRAM_PUBLISHED(false),
    LEARN_DISCUSSION_POST_COMMENT(false),
    LEARN_DISCUSSION_POST_REPLY(false),
    TRANSFER_UPDATE(false),
    PROFILE_UPDATE(false),
    PROFANITY_CHECK(false),
    DELETED_BATCH(false),
    BP_ASSIGNMENT_UPLOAD(false),
    BP_ASSIGNMENT_EVALUATE(false),
    BP_ASSIGNMENT_SUBMIT(false),
    INSTRUCTOR_ADD_BATCH(false),
    PUBLISHED_NEW_VERSION(false),
    RETIRED(false),
    RETIRE_SCHEDULED(false),
    APPROVED_CONTENT_RETIREMENT(false),
    CONTENT_RETIREMENT_SEVEN_DAYS(false),
    CONTENT_RETIREMENT_ONE_DAYS(false),
    CONTENT_FINALLY_RETIRED(false),
    RETIRE_REJECTED(false),
    RETIRE_APPROVED(false),
    CONTENT_RETIRE(false);
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
