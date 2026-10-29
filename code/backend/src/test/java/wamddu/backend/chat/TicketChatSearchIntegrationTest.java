package wamddu.backend.chat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.service.EventService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class TicketChatSearchIntegrationTest {
    @Autowired EntityManager em;
    @Autowired EventService events;

    @Test void categorySearchFindsAnExhibitionWithoutRegisteredTickets() {
        Event exhibition = new Event();
        exhibition.setName("별의 정원");
        exhibition.setCategory("exhibition");
        exhibition.setLocation("서울특별시 마포구 홍익로 3");
        exhibition.setStartDate(LocalDate.of(2026, 10, 10));
        exhibition.setEndDate(LocalDate.of(2026, 12, 31));
        exhibition.setBannerImageUrl("banner");
        exhibition.setMainImageUrl("poster");
        em.persist(exhibition);
        var gemini = mock(GeminiSearchClient.class);
        when(gemini.extract(any())).thenReturn(new GeminiSearchClient.Intent(
                true, "exhibition", null, "서울", null, null, null, null));

        var chat = new TicketChatService(gemini, events);
        var reply = chat.chat(List.of("서울 전시 찾아줘"));

        assertThat(reply.events()).extracting("id").containsExactly(exhibition.getId());
        assertThat(reply.message()).doesNotContain("예매 가능한", "이후");

        when(gemini.extract(any())).thenReturn(new GeminiSearchClient.Intent(
                true, "exhibition", null, "서울", null, null, null, true));
        var bookable = chat.chat(List.of("서울 전시 찾아줘", "예매 가능한 것만"));
        assertThat(bookable.events()).isEmpty();
        assertThat(bookable.message()).contains("예매 가능한 공연이 없습니다");
    }
}
