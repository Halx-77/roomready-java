package com.roomready.room;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes each event to the events table and to the log.
 * The facts are ids and numbers, never the guest.
 * Every event also leaves one log line on stdout - its kind, the room, who, and its facts.
 */
@Component
public class EventLog {

    public static final String SUPERVISOR = "supervisor";
    public static final String FRONT_DESK = "front-desk";

    public record Row(long id, String kind, String room, String actorId, Map<String, Object> facts) {
    }

    private static final Logger log = LoggerFactory.getLogger("com.roomready.events");

    private final JdbcClient jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

        public void append(long propertyId, String room, long stayId, long assignmentId,
                       String kind, Instant at, String actorId, Map<String, ?> facts) {
        try {
            String dataJson = mapper.writeValueAsString(facts != null ? facts : Map.of());
            Long safeAssignmentId = assignmentId == 0 ? null : assignmentId;
            
            jdbc.sql("""
                    INSERT INTO events (property_id, room, stay_id, assignment_id, kind, at, actor_id, data)
                    VALUES (:propertyId, :room, :stayId, :assignmentId, :kind, :at, :actorId, :data)
                    """)
                    .param("propertyId", propertyId)
                    .param("room", room) // <--- THIS MUST BE "room", NOT "roomId"
                    .param("stayId", stayId)
                    .param("assignmentId", safeAssignmentId) 
                    .param("kind", kind)
                    .param("at", at.atOffset(java.time.ZoneOffset.UTC)) 
                    .param("actorId", actorId)
                    .param("data", dataJson)
                    .update();
            
            log.info("event kind={} room={} actor={} facts={}", kind, room, actorId, facts);
        } catch (Exception e) {
            log.error("FAILED TO APPEND EVENT", e); 
            throw new RuntimeException("Failed to append event", e);
        }
    }

    public <T> List<T> forStay(long propertyId, String room, long stayId, BiFunction<Row, Instant, T> mapperFunc) {
        return jdbc.sql("""
                SELECT id, kind, room, actor_id, at, data
                FROM events
                WHERE property_id = :propertyId AND room = :room AND stay_id = :stayId
                ORDER BY id
                """)
                .param("propertyId", propertyId)
                .param("room", room)
                .param("stayId", stayId)
                .query((rs, n) -> {
                    try {
                        String dataJson = rs.getString("data"); // <--- Changed to "data"
                        Map<String, Object> facts = dataJson == null || dataJson.isBlank()
                                ? Map.of()
                                : mapper.readValue(dataJson, new TypeReference<>() {});

                        Row row = new Row(rs.getLong("id"), rs.getString("kind"), rs.getString("room"), rs.getString("actor_id"), facts);
                        Instant at = rs.getTimestamp("at").toInstant();
                        return mapperFunc.apply(row, at);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Failed to deserialize data", e);
                    }
                })
                .list();
    }
}