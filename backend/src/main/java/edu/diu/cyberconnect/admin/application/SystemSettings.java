package edu.diu.cyberconnect.admin.application;

/** Public read API for platform settings. Keys are listed in {@link SettingKeys}. */
public interface SystemSettings {

    boolean getBoolean(String key);

    int getInt(String key);

    String getString(String key);
}
