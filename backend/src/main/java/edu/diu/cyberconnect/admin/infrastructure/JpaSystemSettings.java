package edu.diu.cyberconnect.admin.infrastructure;

import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.admin.domain.SystemSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class JpaSystemSettings implements SystemSettings {

    private final SystemSettingRepository repository;
    private final ObjectMapper objectMapper;

    public JpaSystemSettings(SystemSettingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean getBoolean(String key) {
        return node(key).asBoolean();
    }

    @Override
    public int getInt(String key) {
        return node(key).asInt();
    }

    @Override
    public String getString(String key) {
        JsonNode node = node(key);
        return node.isString() ? node.asString() : node.toString();
    }

    private JsonNode node(String key) {
        SystemSetting setting = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Missing system setting: " + key));
        return objectMapper.readTree(setting.getValue());
    }
}
