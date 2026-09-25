package wamddu.backend.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.response.*;
import wamddu.backend.event.repository.EventRepository;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private EventService eventService;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(101L);
        event.setName("오페라의 유령");
        event.setStartDate(LocalDate.of(2026, 9, 1));
        event.setEndDate(LocalDate.of(2026, 9, 30));
        event.setLocation("드림씨어터");
        event.setBannerImageUrl("http://example.com/banner.jpg");
        event.setMainImageUrl("http://example.com/main.jpg");
        event.setCategory("musical");
        event.setDescription("최고의 뮤지컬");
        event.setRunning_time(150);
    }

    @Test
    @DisplayName("이벤트 목록 조회 성공 테스트 - 전체 카테고리")
    void getEvents_AllCategories_Success() {
        // given
        EventSummaryResponse dto = new EventSummaryResponse(
                101L, "오페라의 유령", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "드림씨어터", "http://example.com/main.jpg", "musical", "최고의 뮤지컬"
        );
        given(eventRepository.getEventsByCategory(null)).willReturn(List.of(dto));

        // when
        EventListResponse response = eventService.getEvents(null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).getName()).isEqualTo("오페라의 유령");
    }

    @Test
    @DisplayName("이벤트 목록 조회 실패 - 유효하지 않은 카테고리")
    void getEvents_InvalidCategory_ThrowsApiException() {
        // given
        given(eventRepository.findAllCategories()).willReturn(List.of("musical", "concert"));

        // when & then
        assertThatThrownBy(() -> eventService.getEvents("invalid_category"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_EVENT_CATEGORY");
    }

    @Test
    @DisplayName("WHAT'S HOT 이벤트 조회 성공 테스트")
    void whatshot_Success() {
        // given
        WhatsHotEventResponse dto = new WhatsHotEventResponse(
                1L, 101L, "오페라의 유령", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "드림씨어터", "http://example.com/banner.jpg", "http://example.com/main.jpg", "musical"
        );
        given(eventRepository.findAllCategories()).willReturn(List.of("musical"));
        given(eventRepository.whatshot(eq("musical"), eq(5))).willReturn(List.of(dto));

        // when
        WhatsHotResponse response = eventService.whatshot("musical", 5);

        // then
        assertThat(response).isNotNull();
        assertThat(response.category()).isEqualTo("musical");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).getName()).isEqualTo("오페라의 유령");
    }

    @Test
    @DisplayName("WHAT'S HOT 조회 실패 - 유효하지 않은 limit")
    void whatshot_InvalidLimit_ThrowsApiException() {
        // when & then
        assertThatThrownBy(() -> eventService.whatshot("musical", 0))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_LIMIT");
    }

    @Test
    @DisplayName("이벤트 상세 및 티켓 옵션 조회 성공 테스트")
    void getDetail_Success() {
        // given
        Ticket ticket = new Ticket(1L, "VIP석", 150000, 100, 10, "최고의 자리", LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(10), event);

        given(eventRepository.findById(101L)).willReturn(Optional.of(event));
        given(ticketRepository.getAllEventTickets(101L)).willReturn(List.of(ticket));

        // when
        EventDetailResponse response = eventService.getDetail(101L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.event()).isNotNull();
        assertThat(response.event().getName()).isEqualTo("오페라의 유령");
        assertThat(response.event().getRunningTime()).isEqualTo(150);
        assertThat(response.ticketOptions()).hasSize(1);
    }

    @Test
    @DisplayName("이벤트 상세 조회 실패 - 존재하지 않는 이벤트")
    void getDetail_NotFound_ThrowsApiException() {
        // given
        given(eventRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getDetail(999L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("EVENT_NOT_FOUND");
    }
}
