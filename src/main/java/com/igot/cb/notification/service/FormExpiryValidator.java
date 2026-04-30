package com.igot.cb.notification.service;

import java.util.List;
import java.util.Map;

/** Marks PENDING notification records as EXPIRED when their form's {@code endDate} has passed, using ES as the authoritative source. */
public interface FormExpiryValidator {

  /** Mutates status to EXPIRED in-place for any PENDING record whose form endDate is past; returns the same list reference, never {@code null}. */
  List<Map<String, Object>> validateAndMarkExpired(List<Map<String, Object>> records);

}
