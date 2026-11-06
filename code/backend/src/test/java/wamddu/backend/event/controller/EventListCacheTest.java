package wamddu.backend.event.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisHashCommands;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wamddu.backend.event.dto.request.EventSearchCriteria;
import wamddu.backend.event.dto.response.EventPageResponse;
import wamddu.backend.event.dto.response.EventSummaryResponse;
import wamddu.backend.event.service.EventService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "event-list-cache.enabled=true")
@AutoConfigureMockMvc
class EventListCacheTest {
    @Autowired MockMvc mvc;
    @MockitoBean EventService events;
    @MockitoBean StringRedisTemplate redis;
    private RedisHashCommands values;
    private final ConcurrentHashMap<String, byte[]> stored = new ConcurrentHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        stored.clear();
        var connection = mock(RedisConnection.class);
        values = mock(RedisHashCommands.class);
        var scripts = mock(RedisScriptingCommands.class);
        when(connection.hashCommands()).thenReturn(values);
        when(connection.scriptingCommands()).thenReturn(scripts);
        when(redis.execute(any(RedisCallback.class))).thenAnswer(call ->
                ((RedisCallback<?>) call.getArgument(0)).doInRedis(connection));
        when(values.hGet(any(byte[].class), any(byte[].class))).thenAnswer(call -> stored.get(new String(call.getArgument(1), StandardCharsets.UTF_8)));
        when(scripts.eval(any(byte[].class), any(), eq(1), any(byte[][].class))).thenAnswer(call -> {
            stored.put(new String(call.getArgument(4), StandardCharsets.UTF_8), call.getArgument(5));
            return 1L;
        });
        when(events.getEventPage(any(EventSearchCriteria.class), anyInt(), anyInt(), anyString()))
                .thenAnswer(call -> response("공연", call.getArgument(1), call.getArgument(2)));
    }

    @Test
    void repeatedUnfilteredRequestsReuseJsonForThirtySeconds() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/events")).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.message").value("이벤트 목록 조회에 성공했습니다."))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(30))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.events[0].name").value("공연"))
                    .andExpect(jsonPath("$.data.events[0].startDate").value("2027-01-01"));
        }
        verify(events, times(1)).getEventPage(any(EventSearchCriteria.class), eq(0), eq(30), eq("latest"));
    }

    @Test
    void filteredRequestsBypassTheGlobalCache() throws Exception {
        String[][] parameters = {{"title", "공연"}, {"region", "서울"},
                {"dateFrom", "2027-01-01"}, {"dateTo", "2027-02-01"}, {"maxPrice", "50000"},
                {"availableOnly", "true"}, {"availableOnly", "false"}};
        for (String[] parameter : parameters) {
            mvc.perform(get("/api/events").param(parameter[0], parameter[1])).andExpect(status().isOk());
        }
        verify(events, times(parameters.length)).getEventPage(any(EventSearchCriteria.class), eq(0), eq(30), eq("latest"));
        verifyNoInteractions(redis);
    }

    @Test
    void pagesCategoriesSizesAndSortsHaveSeparateCachedResponses() throws Exception {
        when(events.getEventPage(any(EventSearchCriteria.class), anyInt(), anyInt(), anyString()))
                .thenAnswer(call -> response(((EventSearchCriteria) call.getArgument(0)).category() + ":" +
                        call.getArgument(3) + ":" + call.getArgument(1) + ":" + call.getArgument(2),
                        call.getArgument(1), call.getArgument(2)));
        String[][] variants = {{"concert", "latest", "0", "30"}, {"concert", "latest", "1", "30"},
                {"musical", "latest", "0", "30"}, {"concert", "closingSoon", "0", "30"},
                {"concert", "latest", "0", "5"}};
        for (String[] v : variants) {
            for (int i = 0; i < 2; i++) {
                mvc.perform(get("/api/events").param("category", v[0]).param("sort", v[1])
                                .param("page", v[2]).param("size", v[3]))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.events[0].name").value(String.join(":", v)));
            }
        }
        verify(events, times(variants.length)).getEventPage(any(EventSearchCriteria.class), anyInt(), anyInt(), anyString());
    }

    @Test
    void invalidPaginationDoesNotReadRedis() throws Exception {
        mvc.perform(get("/api/events").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("sort", "unknown")).andExpect(status().isBadRequest());
        verifyNoInteractions(redis, events);
    }

    @Test
    void aMissingExpiredEntryLoadsFreshData() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        stored.clear();
        when(events.getEventPage(any(EventSearchCriteria.class), anyInt(), anyInt(), anyString()))
                .thenReturn(response("새 공연", 0, 30));
        mvc.perform(get("/api/events")).andExpect(jsonPath("$.data.events[0].name").value("새 공연"));
        verify(events, times(2)).getEventPage(any(EventSearchCriteria.class), eq(0), eq(30), eq("latest"));
    }

    @Test
    void redisFailureStillReturnsTheDatabaseResponse() throws Exception {
        when(values.hGet(any(byte[].class), any(byte[].class))).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        mvc.perform(get("/api/events")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].name").value("공연"));
    }

    private EventPageResponse response(String name, int page, int size) {
        return new EventPageResponse(List.of(new EventSummaryResponse(1L, name,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), "서울", "poster", "concert", "설명")), page, size, false);
    }
}
