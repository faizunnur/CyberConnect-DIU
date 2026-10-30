package edu.diu.cyberconnect.admin.api;

import edu.diu.cyberconnect.admin.application.SettingsAdministration;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.StepUpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
public class AdminSettingsController {

    private final SettingsAdministration settings;
    private final StepUpService stepUp;

    public AdminSettingsController(SettingsAdministration settings, StepUpService stepUp) {
        this.settings = settings;
        this.stepUp = stepUp;
    }

    @GetMapping
    public List<SettingsAdministration.SettingView> list() {
        return settings.list();
    }

    @PutMapping("/{key}")
    public SettingsAdministration.SettingView update(@PathVariable String key, @Valid @RequestBody UpdateRequest request,
            HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        return settings.update(key, request.value(), CurrentUser.require().getId());
    }

    public record UpdateRequest(@NotBlank @Size(max = 4000) String value) {
    }
}
