package com.example.demo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Shifts stored timestamps into the zone the UI should display.
 *
 * created_at is written by the JVM clock, which is UTC in Docker/k8s since no TZ
 * is set on the image, and the column is `timestamp without time zone` so the
 * offset is never persisted. Read paths reattach UTC and convert, otherwise the
 * UI renders the raw UTC wall clock as if it were local.
 */
@Component
public class TimeMapper {

    private final ZoneId displayZone;

    public TimeMapper(@Value("${app.display-zone:Asia/Kolkata}") String displayZone) {
        this.displayZone = ZoneId.of(displayZone);
    }

    public LocalDateTime toDisplay(LocalDateTime storedUtc) {
        if (storedUtc == null) {
            return null;
        }
        return storedUtc.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(displayZone)
                .toLocalDateTime();
    }
}