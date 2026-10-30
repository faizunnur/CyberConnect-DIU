package edu.diu.cyberconnect.admin.infrastructure;

import edu.diu.cyberconnect.admin.application.SettingsAdministration;
import edu.diu.cyberconnect.admin.domain.SystemSetting;
import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class JpaSettingsAdministration implements SettingsAdministration {

    private final SystemSettingRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public JpaSettingsAdministration(SystemSettingRepository repository, ObjectMapper objectMapper, AuditService audit) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettingView> list() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map(JpaSettingsAdministration::toView)
                .toList();
    }

    @Override
    public SettingView update(String key, String jsonValue, UUID actorId) {
        SystemSetting setting = repository.findById(key)
                .orElseThrow(() -> new NotFoundException("SETTING_NOT_FOUND", "Unknown setting."));
        String canonical;
        try {
            canonical = objectMapper.writeValueAsString(objectMapper.readTree(jsonValue));
        } catch (RuntimeException e) {
            throw new BadRequestException("SETTING_VALUE_INVALID", "The value must be valid JSON.");
        }
        String previous = setting.getValue();
        setting.update(canonical, actorId);
        audit.record(new AuditEntry(actorId, Set.of(), "SETTING_CHANGED", "SETTING", null, AuditEntry.Outcome.SUCCESS,
                Map.of("key", key, "from", previous, "to", canonical), null, null, null));
        return toView(setting);
    }

    private static SettingView toView(SystemSetting s) {
        return new SettingView(s.getKey(), s.getValue(), s.getDescription(), s.getUpdatedAt());
    }
}
