"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect, type ReactNode } from "react";
import { realtime } from "@/lib/realtime/stomp";

/** Keeps a STOMP connection open while a verified member uses the app and refreshes affected queries on push. */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  useEffect(() => {
    return realtime.subscribe((destination) => {
      if (destination.endsWith("/notifications")) {
        queryClient.invalidateQueries({ queryKey: ["notifications"] });
      } else if (destination.endsWith("/messages")) {
        queryClient.invalidateQueries({ queryKey: ["messaging"] });
      }
    });
  }, [queryClient]);
  return <>{children}</>;
}
