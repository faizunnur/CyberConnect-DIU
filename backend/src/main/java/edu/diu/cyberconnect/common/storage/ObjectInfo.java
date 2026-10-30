package edu.diu.cyberconnect.common.storage;

import java.time.Instant;
import java.util.Map;

public record ObjectInfo(String key, long size, String contentType, Map<String, String> metadata, Instant lastModified) {
}
