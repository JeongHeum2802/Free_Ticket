package wamddu.backend.chat;

import org.junit.jupiter.api.Test;
import wamddu.backend.event.dto.request.EventSearchCriteria;
import wamddu.backend.event.dto.response.EventListResponse;
import wamddu.backend.event.dto.response.EventSummaryResponse;
import wamddu.backend.event.service.EventService;
import wamddu.backend.global.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TicketChatServiceTest {
    private final GeminiSearchClient gemini = mock(GeminiSearchClient.class);
    private final EventService events = mock(EventService.class);
    private final TicketChatService service = new TicketChatService(gemini, events);
    private final GeminiSearchClient.Intent intent = new GeminiSearchClient.Intent(true, "musical", null, "서울",
            LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), 50000, true);

    @Test void usesExistingSearchAndReturnsOnlyRealEventsWithLimit() {
        when(gemini.extract(any())).thenReturn(intent);
        when(events.getEvents(any(EventSearchCriteria.class))).thenReturn(new EventListResponse(
                IntStream.rangeClosed(1, 7).mapToObj(i -> new EventSummaryResponse((long) i, "공연" + i,
                        null, null, "서울", null, "musical", null)).toList()));
        var result = service.chat(List.of("서울 뮤지컬"));
        verify(events).getEvents(new EventSearchCriteria("musical", null, "서울",
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), 50000, true));
        assertThat(result.events()).extracting(EventSummaryResponse::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(result.message()).contains("서울", "뮤지컬", "50,000", "7", "5");
    }

    @Test void noMatchesDoesNotInventEvents() {
        when(gemini.extract(any())).thenReturn(intent);
        when(events.getEvents(any(EventSearchCriteria.class))).thenReturn(new EventListResponse(List.of()));
        var result = service.chat(List.of("공연"));
        assertThat(result.events()).isEmpty();
        assertThat(result.message()).contains("없습니다");
    }

    @Test void upperDateBoundDoesNotAddTodayOrBookingAvailability() {
        when(gemini.extract(any())).thenReturn(new GeminiSearchClient.Intent(
                true, "exhibition", null, null, null, LocalDate.of(2026, 9, 30), null, null));
        when(events.getEvents(any(EventSearchCriteria.class))).thenReturn(new EventListResponse(List.of()));
        var reply = service.chat(List.of("2026년 9월 30일까지의 전시"));
        verify(events).getEvents(new EventSearchCriteria("exhibition", null, null,
                null, LocalDate.of(2026, 9, 30), null, null));
        assertThat(reply.message()).contains("2026-09-30까지").doesNotContain("null", "이후", "예매 가능한");
    }

    @Test void broadSearchDoesNotInventFilters() {
        when(gemini.extract(any())).thenReturn(new GeminiSearchClient.Intent(
                true, null, null, null, null, null, null, null));
        when(events.getEvents(any(EventSearchCriteria.class))).thenReturn(new EventListResponse(List.of()));
        var reply = service.chat(List.of("공연 찾아줘"));
        verify(events).getEvents(new EventSearchCriteria(null, null, null, null, null, null, null));
        assertThat(reply.message()).contains("전체 공연").doesNotContain("null", "예매 가능한");
    }

    @Test void unrelatedQuestionDoesNotQueryDatabase() {
        when(gemini.extract(any())).thenReturn(new GeminiSearchClient.Intent(false, null, null, null, null, null, null, null));
        assertThat(service.chat(List.of("안녕")).events()).isEmpty();
        verifyNoInteractions(events);
    }

    @Test void invalidModelConditionsAreRejectedByExistingValidation() {
        when(gemini.extract(any())).thenReturn(intent);
        when(events.getEvents(any(EventSearchCriteria.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "invalid"));
        assertThatThrownBy(() -> service.chat(List.of("공연"))).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CHAT_INVALID_SEARCH");
    }

    @Test void sharedRollingLimitStopsExtraGeminiCalls() {
        when(gemini.extract(any())).thenReturn(new GeminiSearchClient.Intent(false, null, null, null, null, null, null, null));
        for (int i = 0; i < 10; i++) service.chat(List.of("안녕"));
        assertThatThrownBy(() -> service.chat(List.of("안녕"))).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CHAT_RATE_LIMITED");
        verify(gemini, times(10)).extract(any());
    }
}
