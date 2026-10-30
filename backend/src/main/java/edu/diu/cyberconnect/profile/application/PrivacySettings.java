package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.model.FieldVisibility;
import edu.diu.cyberconnect.common.model.MessagingPolicy;
import edu.diu.cyberconnect.common.model.ProfileVisibility;

public record PrivacySettings(ProfileVisibility profileVisibility, FieldVisibility emailVisibility,
        FieldVisibility organizationVisibility, FieldVisibility locationVisibility,
        FieldVisibility mentorVisibility, boolean searchable, MessagingPolicy messagingPolicy) {
}
