package wamddu.backend.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.response.EventDetailResponseDTO;
import wamddu.backend.event.dto.response.GetEventsResponseDto;
import wamddu.backend.event.dto.response.WeeklyRankingResponseDto;
import wamddu.backend.event.dto.response.WhatsHotResponseDto;
import wamddu.backend.event.repository.EventRepository;
import wamddu.backend.global.response.BusinessException;
import wamddu.backend.global.response.ErrorCode;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public List<GetEventsResponseDto> getEvents(String category) {
        if(category != null){
            List<String> allCategories = eventRepository.findAllCategories();

            if(!allCategories.contains(category)){
                throw new BusinessException(ErrorCode.INVALID_EVENT_CATEGORY);
            }
        }

        return eventRepository.getEventsByCategory(category);
    }

    public List<WhatsHotResponseDto> whatshot(String category, Integer limit) {
        //잘못된 limit
        if(limit < 1 || limit > 20){
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }

        //잘못된 카테고리
        List<String> categories = eventRepository.findAllCategories();
        if(category != null && !categories.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_CATEGORY);
        }

        Pageable pageable = PageRequest.of(0, limit);
        return eventRepository.whatshot(category, pageable);
    }

    public List<WeeklyRankingResponseDto> weeklyRanking(String category, Integer limit) {
        //잘못된 limit
        if(limit < 1 || limit > 20){
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }

        //잘못된 카테고리
        List<String> categories = eventRepository.findAllCategories();
        if(category != null && !categories.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_CATEGORY);
        }

        Pageable pageable = PageRequest.of(0, limit);
        return eventRepository.weeklyRanking(category, pageable);
    }

    public EventDetailResponseDTO getDetail(Long id) {
        Event event = eventRepository.findById(id).orElse(null);

        if(event == null) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }

        List<Ticket> tickets = ticketRepository.getAllEventTickets(id);

        return EventDetailResponseDTO.create(event, tickets);
    }
}
