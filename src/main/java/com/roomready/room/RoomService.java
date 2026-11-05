package com.roomready.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.roomready.config.RoomReadySettings;

import com.roomready.error.NoSuchProperty;
import com.roomready.error.NoSuchRoom;

/**  The board: every room with its state, derived from its rows on every read; and the housekeepers.  */
@Service
public class RoomService {

    private final PropertyRepository properties;
    private final RoomRepository rooms;
    private final HousekeeperRepository housekeepers;
    private final StayRepository stays;
    private final EventLog events;
    private final RoomReadySettings settings;
    private final Clock clock;

    public RoomService(PropertyRepository properties, RoomRepository rooms, HousekeeperRepository housekeepers,
                       StayRepository stays, EventLog events, RoomReadySettings settings, Clock clock) {
        this.properties = properties;
        this.rooms = rooms;
        this.housekeepers = housekeepers;
        this.stays = stays;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    /**  The board, optionally one floor, one state and/or one type; ready and dirty count the rooms shown.  */
    public BoardView board(String property, Integer floor, RoomState state, RoomType type) {
        List<RoomView> shown = rooms.all(propertyId(property)).stream()
                .map(this::view)
                .filter(r -> floor == null || r.floor() == floor)
                .filter(r -> state == null || r.state() == state)
                .filter(r -> type == null || r.type() == type)
                .toList();
        return new BoardView(property, count(shown, RoomState.ready), count(shown, RoomState.dirty), shown);
    }

    public RoomView room(String property, String no) {
        return view(rooms.one(propertyId(property), no).orElseThrow(() -> new NoSuchRoom(property, no)));
    }

    /**
     * A room's day: the events of one stay - by default the current one. A stay that is not this room's, or not a
     * stay at all, has no events here.
     */
    public HistoryView history(String property, String no, String stay) {
        long propertyId = propertyId(property);
        room(property, no);
        long stayId = stay == null ? stays.current(propertyId, no).orElseThrow().id()
                : StayIds.parse(stay).orElse(0L);
        return new HistoryView(no, events.forStay(propertyId, no, stayId, (row, at) -> 
        new EventView(at, row.kind(), (Map<String, Object>) (Map<?, ?>) row.facts())));
    }

    /**  Every housekeeper, with the room of an open assignment they hold (a room assigned, cleaning or to inspect).  */
    public HousekeepersView housekeepers(String property) {
        long propertyId = propertyId(property);
        Map<String, String> open = rooms.all(propertyId).stream()
                .filter(r -> r.housekeeperId() != null && view(r).assignedTo() != null)
                .collect(Collectors.toMap(RoomRepository.Row::housekeeperId, RoomRepository.Row::no));
        return new HousekeepersView(housekeepers.all(propertyId).stream()
                .map(h -> new HousekeeperView(h.id(), h.name(), open.get(h.id()))).toList());
    }

    private long propertyId(String property) {
        return properties.idOf(property).orElseThrow(() -> new NoSuchProperty(property));
    }

    /**
     * Derived on every read. Out of order overrides all; a room is occupied while its latest stay has no checkout,
     * dirty once it has, and then assigned, cleaning or to inspect by the stamps of that stay's assignment - which,
     * while it is open, also gives who is on the room and for how many minutes it has been in this state. A failed
     * inspection closes the assignment: the room is dirty again until the next one; a passed one closes it
     * with the room ready. A room whose type needs no inspection is ready as soon as its clean is
     * finished.
     */
    private RoomView view(RoomRepository.Row row) {
        RoomState state = state(row);
        Instant since = switch (state) {
            case assigned -> row.assignedAt();
            case cleaning -> row.startedAt();
            case toinspect -> row.finishedAt();
            default -> null;
        };
        if (since == null) {
            return new RoomView(row.no(), row.floor(), row.type(), state, null, null);
        }
        return new RoomView(row.no(), row.floor(), row.type(), state, row.assignedTo(),
                (int) Duration.between(since, clock.instant()).toMinutes());
    }

        private RoomState state(RoomRepository.Row row) {
        if (row.outOfOrder()) {
            return RoomState.out_of_order;
        }
        if (row.checkedOutAt() == null) {
            return RoomState.occupied;
        }
        if (row.assignedAt() == null || row.failed()) {
            return RoomState.dirty;
        }
        if (row.passed()) {
            return RoomState.ready;
        }
        if (row.startedAt() == null) {
            return RoomState.assigned;
        }
        if (row.finishedAt() == null) {
            return RoomState.cleaning;
        }
        
        // FIX: Compare the String list with the enum's name (case-insensitive)
        String typeStr = row.type().name();
        boolean requiresInspection = settings.inspectionRequiredFor().stream()
                .anyMatch(t -> t.trim().equalsIgnoreCase(typeStr));

        return requiresInspection ? RoomState.toinspect : RoomState.ready;
    }

    private static int count(List<RoomView> shown, RoomState state) {
        return (int) shown.stream().filter(r -> r.state() == state).count();
    }
}