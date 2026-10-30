package edu.diu.cyberconnect.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Administrative read/write access to platform settings. Every change is audited. */
public interface SettingsAdministration {

    record SettingView(String key, String value, String description, Instant updatedAt) {
    }

    List<SettingView> list();

    SettingView update(String key, String jsonValue, UUID actorId);
}
