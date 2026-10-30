package edu.diu.cyberconnect.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountStatusTest {

    @Test
    void followsTheApprovedStateMachine() {
        assertThat(AccountStatus.PENDING.canTransitionTo(AccountStatus.VERIFIED)).isTrue();
        assertThat(AccountStatus.PENDING.canTransitionTo(AccountStatus.REJECTED)).isTrue();
        assertThat(AccountStatus.PENDING.canTransitionTo(AccountStatus.SUSPENDED)).isFalse();
        assertThat(AccountStatus.REJECTED.canTransitionTo(AccountStatus.PENDING)).isTrue();
        assertThat(AccountStatus.REJECTED.canTransitionTo(AccountStatus.VERIFIED)).isTrue();
        assertThat(AccountStatus.VERIFIED.canTransitionTo(AccountStatus.SUSPENDED)).isTrue();
        assertThat(AccountStatus.VERIFIED.canTransitionTo(AccountStatus.PENDING)).isFalse();
        assertThat(AccountStatus.SUSPENDED.canTransitionTo(AccountStatus.VERIFIED)).isTrue();
        assertThat(AccountStatus.SUSPENDED.canTransitionTo(AccountStatus.REJECTED)).isFalse();
        assertThat(AccountStatus.DEACTIVATED.canTransitionTo(AccountStatus.VERIFIED)).isTrue();
        assertThat(AccountStatus.DEACTIVATED.canTransitionTo(AccountStatus.SUSPENDED)).isFalse();
    }

    @Test
    void onlyVerifiedHasCommunityAccess() {
        for (AccountStatus status : AccountStatus.values()) {
            assertThat(status.hasCommunityAccess()).isEqualTo(status == AccountStatus.VERIFIED);
        }
    }
}
