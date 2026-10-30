package edu.diu.cyberconnect.networking.application;

public record NetworkSummary(long connections, long pendingIncoming, long pendingOutgoing, long blocked) {
}
