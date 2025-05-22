package com.igot.cb.health.service;

import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthServiceImpl implements HealthService {

    @Autowired
    CassandraOperation cassandraOperation;

    private Logger log = LoggerFactory.getLogger(getClass().getName());

    @Override
    public ApiResponse checkHealthStatus() throws Exception {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_HEALTH_CHECK);
        try {
            response.put(Constants.HEALTHY, true);
            List<Map<String, Object>> healthResults = new ArrayList<>();
            response.put(Constants.CHECKS, healthResults);
            cassandraHealthStatus(response);
        } catch (Exception e) {
            log.error("Failed to process health check. Exception: ", e);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErr(e.getMessage());
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    public void cassandraHealthStatus(ApiResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, Constants.CASSANDRA_DB);

        boolean isHealthy = false;
        try {
            List<Map<String, Object>> cassandraQueryResponse = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_SYSTEM_SETTINGS,
                    null,
                    null,
                    1
            );
            isHealthy = !cassandraQueryResponse.isEmpty();
        } catch (Exception e) {
            log.error("Cassandra health check failed: {}", e.getMessage(), e);
        }

        result.put(Constants.HEALTHY, isHealthy);
        ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);
        response.put(Constants.HEALTHY, isHealthy);
    }

}

