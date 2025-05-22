package com.igot.cb.util;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Getter
@Setter
@Data
public class ApiResponse {
    private String id;
    private String ver;
    private String ts;
    private ApiRespParam params;
    private HttpStatus responseCode;
    public Map<String, Object> getResult() {
        return response;
    }
    public void put(String key, Object vo) {
        response.put(key, vo);
    }

    @Getter(lombok.AccessLevel.NONE)
    private transient Map<String, Object> response = new HashMap<>();

    public ApiResponse() {
        this.ver = "v1";
        this.ts = new Timestamp(System.currentTimeMillis()).toString();
        this.params = new ApiRespParam(UUID.randomUUID().toString());
    }

    public ApiResponse(String id) {
        this();
        this.id = id;
    }

    public Object get(String key) {
        return response.get(key);
    }

    public void setResult(Map<String, Object> result) {
        response = result;
    }
}
