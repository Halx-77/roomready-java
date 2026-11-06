package com.roomready.room;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The append-only log. It can add an event and read a stay's events - nothing else, so nothing in the app can
 * change or remove one. Called inside the transaction of the change it records, so a rolled-back change leaves no
 * event behind. Every event names who did it; the facts are ids and numbers, never the guest.
 */
@Repository
public class EventLog {

    /** Who acts when nobody logs in.  */
    public static final String FRONT_DESK = "front-desk";
    public static final String SUPERVISOR = "supervisor";

    private record Row(Instant at, String kind, String data) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public EventLog(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** One event of a room's stay; assignmentId is null for the checkout.  */
    public void append(long propertyId, String room, long stayId, Long assignmentId, String kind, Instant at,
                       String actorId, Map<String, Object> facts) {
        jdbc.sql("""
                        INSERT INTO events (property_id, room_id, stay_id, assignment_id, kind, at, actor_id, data)
                        SELECT ?, id, ?, ?, ?, ?, ?, ? FROM rooms WHERE property_id = ? AND no = ?""")
                .params(propertyId, stayId, assignmentId, kind, at, actorId, write(facts), propertyId, room)
                .update();
    }

    /** A room's events during one stay, in at order.  */
    public List<EventView> forStay(long propertyId, String room, long stayId) {
        return jdbc.sql("""
                        SELECT e.at, e.kind, e.data FROM events e JOIN rooms r ON r.id = e.room_id
                        WHERE e.property_id = ? AND r.no = ? AND e.stay_id = ? ORDER BY e.at, e.id""")
                .params(propertyId, room, stayId)
                .query(Row.class).list().stream()
                .map(r -> new EventView(r.at(), r.kind(), read(r.data())))
                .toList();
    }

    private String write(Map<String, Object> facts) {
        try {
            return json.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("event facts must be JSON", e);
        }
    }

    private Map<String, Object> read(String data) {
        try {
            return json.readValue(data, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("an events row holds data that is not JSON", e);
        }
    }
}