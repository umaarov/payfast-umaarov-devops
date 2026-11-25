package com.umaarov.logistics.tracking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrackingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String newTrackingNumber() {
        return "LG" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private ResultActions record(UUID eventId, String trackingNumber, String status, String occurredAt)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("eventId", eventId);
        body.put("trackingNumber", trackingNumber);
        body.put("status", status);
        body.put("description", "Status " + status);
        body.put("location", "Tashkent");
        if (occurredAt != null) {
            body.put("occurredAt", occurredAt);
        }
        return mockMvc.perform(post("/internal/tracking-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions record(String trackingNumber, String status) throws Exception {
        return record(UUID.randomUUID(), trackingNumber, status, null);
    }

    @Test
    void createdEventStartsTheTimeline() throws Exception {
        String trackingNumber = newTrackingNumber();

        record(trackingNumber, "CREATED")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(get("/api/tracking/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void timelineListsEventsInOrderAndReportsLatestStatus() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(UUID.randomUUID(), trackingNumber, "CREATED", "2026-11-25T08:00:00Z").andExpect(status().isCreated());
        record(UUID.randomUUID(), trackingNumber, "COURIER_ASSIGNED", "2026-11-25T09:00:00Z")
                .andExpect(status().isCreated());
        record(UUID.randomUUID(), trackingNumber, "PICKED_UP", "2026-11-25T10:00:00Z").andExpect(status().isCreated());
        record(UUID.randomUUID(), trackingNumber, "DELIVERED", "2026-11-25T12:00:00Z").andExpect(status().isCreated());

        mockMvc.perform(get("/api/tracking/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-25T12:00:00Z"))
                .andExpect(jsonPath("$.events.length()").value(4))
                .andExpect(jsonPath("$.events[0].status").value("CREATED"))
                .andExpect(jsonPath("$.events[1].status").value("COURIER_ASSIGNED"))
                .andExpect(jsonPath("$.events[2].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.events[3].status").value("DELIVERED"));
    }

    @Test
    void lateArrivingEventIsPlacedByOccurrenceTime() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(UUID.randomUUID(), trackingNumber, "CREATED", "2026-11-25T08:00:00Z").andExpect(status().isCreated());
        record(UUID.randomUUID(), trackingNumber, "PICKED_UP", "2026-11-25T10:00:00Z").andExpect(status().isCreated());
        record(UUID.randomUUID(), trackingNumber, "COURIER_ASSIGNED", "2026-11-25T09:00:00Z")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tracking/" + trackingNumber))
                .andExpect(jsonPath("$.events[1].status").value("COURIER_ASSIGNED"))
                .andExpect(jsonPath("$.events[2].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void firstEventMustBeCreated() throws Exception {
        String trackingNumber = newTrackingNumber();

        record(trackingNumber, "IN_TRANSIT").andExpect(status().isConflict());

        mockMvc.perform(get("/api/tracking/" + trackingNumber)).andExpect(status().isNotFound());
    }

    @Test
    void parcelCannotBeCreatedTwice() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(trackingNumber, "CREATED").andExpect(status().isCreated());

        record(trackingNumber, "CREATED").andExpect(status().isConflict());
    }

    @Test
    void repeatedEventIdIsIdempotent() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(trackingNumber, "CREATED").andExpect(status().isCreated());
        UUID eventId = UUID.randomUUID();

        record(eventId, trackingNumber, "COURIER_ASSIGNED", null).andExpect(status().isCreated());
        record(eventId, trackingNumber, "COURIER_ASSIGNED", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/tracking/" + trackingNumber))
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void deliveredParcelRejectsFurtherEvents() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(trackingNumber, "CREATED").andExpect(status().isCreated());
        record(trackingNumber, "DELIVERED").andExpect(status().isCreated());

        record(trackingNumber, "IN_TRANSIT").andExpect(status().isConflict());
    }

    @Test
    void cancelledParcelRejectsFurtherEvents() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(trackingNumber, "CREATED").andExpect(status().isCreated());
        record(trackingNumber, "CANCELLED").andExpect(status().isCreated());

        record(trackingNumber, "PICKED_UP").andExpect(status().isConflict());
    }

    @Test
    void failedDeliveryDoesNotCloseTheParcel() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(trackingNumber, "CREATED").andExpect(status().isCreated());
        record(trackingNumber, "DELIVERY_FAILED").andExpect(status().isCreated());

        record(trackingNumber, "IN_TRANSIT").andExpect(status().isCreated());
    }

    @Test
    void unknownTrackingNumberIsNotFound() throws Exception {
        mockMvc.perform(get("/api/tracking/" + newTrackingNumber())).andExpect(status().isNotFound());
    }

    @Test
    void malformedTrackingNumberIsRejected() throws Exception {
        mockMvc.perform(get("/api/tracking/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void invalidEventIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/internal/tracking-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        record(newTrackingNumber(), "TELEPORTED").andExpect(status().isBadRequest());
    }

    @Test
    void publicTimelineDoesNotExposeInternalIdentifiers() throws Exception {
        String trackingNumber = newTrackingNumber();
        record(trackingNumber, "CREATED").andExpect(status().isCreated());

        mockMvc.perform(get("/api/tracking/" + trackingNumber))
                .andExpect(jsonPath("$.events[0].eventId").doesNotExist())
                .andExpect(jsonPath("$.events[0].id").doesNotExist())
                .andExpect(jsonPath("$.events[0].description").value("Status CREATED"))
                .andExpect(jsonPath("$.events[0].location").value("Tashkent"));
    }
}
