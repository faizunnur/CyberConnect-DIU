"use client";

import { Client, type IMessage } from "@stomp/stompjs";
import { readCookie } from "@/lib/api/client";

/**
 * One STOMP connection per browser tab to the same-origin `/ws` endpoint (nginx proxies it to the backend).
 * The handshake carries the session cookie; CONNECT carries the CSRF cookie value as a header (double submit).
 * Nothing is ever sent from the browser besides SUBSCRIBE; the server pushes to the member's user queues.
 */
export type RealtimeListener = (destination: string, payload: unknown) => void;

function endpoint(): string {
  const configured = process.env.NEXT_PUBLIC_WS_URL;
  if (configured) return configured;
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws`;
}

export class RealtimeConnection {
  private client: Client | null = null;
  private readonly listeners = new Set<RealtimeListener>();

  subscribe(listener: RealtimeListener): () => void {
    this.listeners.add(listener);
    this.ensureConnected();
    return () => {
      this.listeners.delete(listener);
      if (this.listeners.size === 0) this.disconnect();
    };
  }

  private ensureConnected() {
    if (this.client || typeof window === "undefined") return;
    const client = new Client({
      brokerURL: endpoint(),
      reconnectDelay: 10_000,
      heartbeatIncoming: 20_000,
      heartbeatOutgoing: 20_000,
      beforeConnect: () => {
        const token = readCookie("XSRF-TOKEN");
        client.connectHeaders = token ? { "X-XSRF-TOKEN": token } : {};
      },
      onConnect: () => {
        for (const destination of ["/user/queue/notifications", "/user/queue/messages"]) {
          client.subscribe(destination, (frame: IMessage) => this.dispatch(destination, frame));
        }
      },
      onStompError: () => {
        // CSRF or authorization failure: do not hammer the server; the UI keeps working through REST polling.
        client.deactivate();
        this.client = null;
      },
    });
    this.client = client;
    client.activate();
  }

  private dispatch(destination: string, frame: IMessage) {
    let payload: unknown = frame.body;
    try {
      payload = JSON.parse(frame.body);
    } catch {
      // plain text payload
    }
    for (const listener of this.listeners) listener(destination, payload);
  }

  private disconnect() {
    const client = this.client;
    this.client = null;
    void client?.deactivate();
  }
}

export const realtime = new RealtimeConnection();
