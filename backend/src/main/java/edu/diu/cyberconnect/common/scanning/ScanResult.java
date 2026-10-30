package edu.diu.cyberconnect.common.scanning;

public record ScanResult(Status status, String signature) {

    public enum Status { CLEAN, INFECTED, ERROR }

    public static ScanResult clean() {
        return new ScanResult(Status.CLEAN, null);
    }

    public static ScanResult infected(String signature) {
        return new ScanResult(Status.INFECTED, signature);
    }

    public static ScanResult error(String reason) {
        return new ScanResult(Status.ERROR, reason);
    }

    public boolean isClean() {
        return status == Status.CLEAN;
    }
}
