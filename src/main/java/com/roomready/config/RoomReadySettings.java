package com.roomready.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The app's own settings, read once at start-up: application.yml gives the defaults, and an environment
 * variable overrides each one. Spring's own settings - the database URL, the host and the port - stay under
 * spring.datasource and server.
 *
 * @param seedDir             the folder holding the seed files (rooms.csv, housekeepers.csv)
 * @param inspectionRequiredFor the room types a finished clean must be inspected for
 * @param stallAfterMinutes   how long a started room may stay unfinished before it is flagged
 */
@ConfigurationProperties("roomready")
public record RoomReadySettings(
        @DefaultValue("seed") String seedDir,
        @DefaultValue("Standard,Deluxe,Suite") List<String> inspectionRequiredFor,
        @DefaultValue("30") int stallAfterMinutes) {
}