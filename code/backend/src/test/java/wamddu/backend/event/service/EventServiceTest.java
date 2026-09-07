package wamddu.backend.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.domain.getEventsResponseDto;
import wamddu.backend.event.domain.weeklyRankingResponseDto;
import wamddu.backend.event.domain.whatshotResponseDto;
import wamddu.backend.event.repository.eventRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.ticketRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private eventRepository eventRepository;

    @Mock
    private ticketRepository ticketRepository;

    @InjectMocks
    private eventService eventService;

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
        getEventsResponseDto dto = new getEventsResponseDto(
                101L, "오페라의 유령", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "드림씨어터", "http://example.com/main.jpg", "musical", "최고의 뮤지컬"
        );
        given(eventRepository.getEventsByCategory(null)).willReturn(List.of(dto));

        // when
        ResponseEntity<Map<String, Object>> response = eventService.getEvents(null);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("이벤트 목록 조회 실패 - 유효하지 않은 카테고리")
    void getEvents_InvalidCategory_ReturnsBadRequest() {
        // given
        given(eventRepository.findAllCategories()).willReturn(List.of("musical", "concert"));

        // when
        ResponseEntity<Map<String, Object>> response = eventService.getEvents("invalid_category");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "INVALID_EVENT_CATEGORY");
    }

    @Test
    @DisplayName("WHAT'S HOT 이벤트 조회 성공 테스트")
    void whatshot_Success() {
        // given
        whatshotResponseDto dto = new whatshotResponseDto(
                1L, 101L, "오페라의 유령", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "드림씨어터", "http://example.com/banner.jpg", "http://example.com/main.jpg", "musical"
        );
        given(eventRepository.findAllCategories()).willReturn(List.of("musical"));
        given(eventRepository.whatshot(eq("musical"), any())).willReturn(List.of(dto));

        // when
        ResponseEntity<Map<String, Object>> response = eventService.whatshot("musical", 5);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "WHAT'S HOT 이벤트 조회에 성공했습니다.");
    }

    @Test
    @DisplayName("이벤트 상세 및 티켓 옵션 조회 성공 테스트")
    void getDetail_Success() {
        // given
        Ticket ticket = new Ticket(1L, "VIP석", 150000, 100, 10, "최고의 자리", LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(10), event);

        given(eventRepository.findById(101L)).willReturn(Optional.of(event));
        given(ticketRepository.getAllEventTickets(101L)).willReturn(List.of(ticket));

        // when
        ResponseEntity<Map<String, Object>> response = eventService.getDetail(101L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }
}
