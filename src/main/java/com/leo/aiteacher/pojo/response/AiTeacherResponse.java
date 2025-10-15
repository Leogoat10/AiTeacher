package com.leo.aiteacher.pojo.response;

import lombok.Data;

@Data
public class AiTeacherResponse {

    private String id;
    private String object;
    private long created;
    private String model;
    private Choice[] choices;
    private Usage usage;
    private String system_fingerprint;

    @Data
    public static class Choice {
        private int index;
        private Message message;
        private Object logprobs;
        private String finish_reason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;
        private PromptTokensDetails prompt_tokens_details;
        private int prompt_cache_hit_tokens;
        private int prompt_cache_miss_tokens;
    }

    @Data
    public static class PromptTokensDetails {
        private int cached_tokens;
    }
}
