package edu.diu.cyberconnect.profile.api;

import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.SkillView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final ProfileService profiles;

    public SkillController(ProfileService profiles) {
        this.profiles = profiles;
    }

    /** Type-ahead suggestions from the shared skill catalogue. */
    @GetMapping
    public List<SkillView> suggest(@RequestParam(required = false) String q) {
        return profiles.suggestSkills(q);
    }
}
