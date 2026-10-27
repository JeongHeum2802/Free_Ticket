package wamddu.backend.event.dto.request;

import java.time.LocalDate;

/** Dates refer to ticket session dates, inclusively. Null fields do not filter results. */
public record EventSearchCriteria(
        String category, String title, String region,
        LocalDate dateFrom, LocalDate dateTo, Integer maxPrice, Boolean availableOnly
) {
    public boolean hasTicketFilters() {
        return dateFrom != null || dateTo != null || maxPrice != null || Boolean.TRUE.equals(availableOnly);
    }
}
