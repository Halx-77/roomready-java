package com.roomready.config;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.roomready.room.RoomType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The live settings, read-only - the rule book screen reads them from here.  */
@RestController
@Tag(name = "settings", description = "The rules the app is running with")
public class SettingsController {

    private final RoomReadySettings settings;

    public SettingsController(RoomReadySettings settings) {
        this.settings = settings;
    }

        @GetMapping("/settings")
    public SettingsView settings() {
        List<String> required = settings.inspectionRequiredFor();
        if (required == null) {
            required = List.of();
        }

        return new SettingsView(
                required.stream()
                        .map(String::trim) // Just trim spaces, don't force uppercase!
                        .map(RoomType::valueOf)
                        .toList(), 
                settings.stallAfterMinutes()
        );
    }
}