package edu.diu.cyberconnect.profile.api;

import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.profile.application.DirectoryQuery;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfilePhoto;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.ProfileView;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Directory and other members' profiles, always filtered through the owner's privacy settings. */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final ProfileService profiles;

    public MemberController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public Page<MemberCard> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) AffiliationType affiliation,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) Boolean mentorAvailable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        DirectoryQuery query = new DirectoryQuery(q, affiliation, skill, organization, experience, mentorAvailable);
        return profiles.search(CurrentUser.require().getId(), query,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/{userId}")
    public ProfileView view(@PathVariable UUID userId) {
        return profiles.view(CurrentUser.require().getId(), userId);
    }

    @GetMapping("/{userId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID userId) {
        ProfilePhoto photo = profiles.photo(CurrentUser.require().getId(), userId)
                .orElseThrow(() -> new NotFoundException("PHOTO_NOT_FOUND", "No photo."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(photo.content());
    }
}
