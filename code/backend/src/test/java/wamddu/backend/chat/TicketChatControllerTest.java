package wamddu.backend.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.config.SecurityConfig;
import wamddu.backend.global.security.JwtProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketChatController.class)
@Import(SecurityConfig.class)
class TicketChatControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketChatService service;
    @MockitoBean JwtProvider jwt;
    private final ObjectMapper json = new ObjectMapper();

    @Test void guestCanSearchWithoutAuthentication() throws Exception {
        when(service.chat(List.of("서울 뮤지컬"))).thenReturn(new TicketChatService.Reply("검색 결과", List.of()));
        mvc.perform(post("/api/events/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[\"서울 뮤지컬\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("검색 결과"))
                .andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test void invalidInputNeverCallsGemini() throws Exception {
        for (String body : List.of("{}", "{\"messages\":null}", "{\"messages\":[]}",
                "{\"messages\":[null]}", "{\"messages\":[\"   \"]}",
                json.writeValueAsString(Map.of("messages", List.of("a".repeat(1001)))),
                json.writeValueAsString(Map.of("messages", Collections.nCopies(7, "공연"))))) {
            mvc.perform(post("/api/events/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
