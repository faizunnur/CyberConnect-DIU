package edu.diu.cyberconnect.community.application;

public final class CommunityAuditActions {

    public static final String POST_REMOVED = "COMMUNITY_POST_REMOVED";
    public static final String POST_RESTORED = "COMMUNITY_POST_RESTORED";
    public static final String COMMENT_REMOVED = "COMMUNITY_COMMENT_REMOVED";
    public static final String COMMENT_RESTORED = "COMMUNITY_COMMENT_RESTORED";
    public static final String REPORT_RESOLVED = "COMMUNITY_REPORT_RESOLVED";

    public static final String TARGET_POST = "POST";
    public static final String TARGET_COMMENT = "COMMENT";
    public static final String TARGET_REPORT = "REPORT";

    private CommunityAuditActions() {
    }
}
