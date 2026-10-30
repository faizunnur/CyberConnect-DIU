package edu.diu.cyberconnect.identity.application;

public record LoginResult(boolean mfaRequired, MeResponse user) {

    public static LoginResult requiresMfa() {
        return new LoginResult(true, null);
    }

    public static LoginResult authenticated(MeResponse user) {
        return new LoginResult(false, user);
    }
}
