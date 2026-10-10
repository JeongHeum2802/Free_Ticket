package wamddu.backend.event.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.repository.EventRepository;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventSearchTest {
    @Autowired MockMvc mvc;
    @Autowired EventRepository events;

    @BeforeEach
    void seedEvents() {
        event("여름밤 콘서트", "전라남도 여수시 박람회길 1", "concert");
        event("여름밤 콘서트", "서울특별시 송파구 올림픽로 1", "concert");
        event("여름밤 뮤지컬", "전라남도 여수시 중앙로 1", "musical");
        event("겨울 콘서트", "전라남도 여수시 중앙로 2", "concert");
    }

    @Test
    void anonymousSearchCombinesTitleRegionAndCategory() throws Exception {
        mvc.perform(get("/api/events").param("title", "  여름밤  ")
                        .param("region", " 여수시 ").param("category", "concert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].name").value("여름밤 콘서트"))
                .andExpect(jsonPath("$.data.events[0].location").value("전라남도 여수시 박람회길 1"));
    }

    @Test
    void filtersWorkIndependentlyAndBlankValuesMeanAll() throws Exception {
        mvc.perform(get("/api/events").param("region", "여수"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(3));
        mvc.perform(get("/api/events").param("title", "겨울"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(1));
        mvc.perform(get("/api/events").param("category", "musical"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(1));
        mvc.perform(get("/api/events").param("title", " ").param("region", " ").param("category", " "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(4));
        mvc.perform(get("/api/events"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(4));
    }

    @Test
    void supportedCategoryWithNoEventsReturnsEmptyResults() throws Exception {
        mvc.perform(get("/api/events").param("category", "busking"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    void titleAndRegionTreatLikeWildcardsAsLiteralText() throws Exception {
        event("100%_! 라이브", "특별%_!공연장", "concert");
        mvc.perform(get("/api/events").param("title", "%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].name").value("100%_! 라이브"));
        mvc.perform(get("/api/events").param("region", "%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(1));
    }

    @Test
    void unmatchedAndSqlLikeTextReturnEmptyResults() throws Exception {
        mvc.perform(get("/api/events").param("title", "없는 제목"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events").isEmpty());
        mvc.perform(get("/api/events").param("title", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    void oversizedInputAndInvalidCategoryReturnBadRequest() throws Exception {
        for (String field : new String[]{"title", "region", "category"}) {
            mvc.perform(get("/api/events").param(field, "가".repeat(101)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/events").param("category", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_CATEGORY"));
    }

    private void event(String name, String location, String category) {
        Event event = new Event();
        event.setName(name);
        event.setLocation(location);
        event.setCategory(category);
        event.setStartDate(LocalDate.of(2026, 10, 10));
        event.setEndDate(LocalDate.of(2026, 10, 11));
        event.setBannerImageUrl("banner");
        event.setMainImageUrl("poster");
        events.save(event);
    }
}
