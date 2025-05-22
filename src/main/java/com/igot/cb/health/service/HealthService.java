package com.igot.cb.health.service;


import com.igot.cb.util.ApiResponse;

public interface HealthService {

    ApiResponse checkHealthStatus() throws Exception;

}
