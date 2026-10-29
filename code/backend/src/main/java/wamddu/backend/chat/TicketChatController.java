package wamddu.backend.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.global.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TicketChatController {
    private final TicketChatService service;

    public record Request(@NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 1000) String> messages) {}

    @PostMapping("/api/events/chat")
    public ApiResponse<TicketChatService.Reply> chat(@Valid @RequestBody Request request) {
        return ApiResponse.success(service.chat(request.messages()));
    }
}
