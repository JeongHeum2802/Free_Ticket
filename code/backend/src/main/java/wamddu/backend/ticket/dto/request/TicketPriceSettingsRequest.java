package wamddu.backend.ticket.dto.request;

import jakarta.validation.constraints.Positive;

public record TicketPriceSettingsRequest(@Positive Integer initialPrice,
        @Positive Integer minPrice, boolean automaticPricingEnabled,
        @Positive Integer price) {}
