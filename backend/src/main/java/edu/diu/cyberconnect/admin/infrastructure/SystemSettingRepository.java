package edu.diu.cyberconnect.admin.infrastructure;

import edu.diu.cyberconnect.admin.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
