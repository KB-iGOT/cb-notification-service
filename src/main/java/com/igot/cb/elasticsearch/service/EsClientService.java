package com.igot.cb.elasticsearch.service;

import java.util.List;
import java.util.Map;


public interface EsClientService {

  /** Bool-filter terms query; optionally narrows by {@code contextType}; projects only requested {@code fields}. Never returns {@code null}. */
  List<Map<String, Object>> searchByTerms(String esIndexName, String fieldName, List<String> values, String contextType, List<String> fields);
}
