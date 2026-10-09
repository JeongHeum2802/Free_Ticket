package wamddu.backend.ticket.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.domain.TicketPriceHistory;
import wamddu.backend.ticket.dto.request.TicketPriceSettingsRequest;
import wamddu.backend.ticket.repository.TicketPriceHistoryRepository;
import wamddu.backend.ticket.repository.TicketRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketPricingServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 0);
    @Mock TicketRepository tickets;
    @Mock OrderRepository orders;
    @Mock TicketPriceHistoryRepository history;
    @InjectMocks TicketPricingService service;

    private Ticket ticket() {
        var event = new Event();
        event.setId(101L);
        event.setName("행사");
        var ticket = new Ticket();
        ticket.setId(1L);
        ticket.setEvent(event);
        ticket.setPrice(50000);
        ticket.setInitialPrice(50000);
        ticket.setMinPrice(20000);
        ticket.setTotal_ticket(1000);
        ticket.setSold_ticket(400);
        ticket.setSalesStartAt(NOW.minusDays(2));
        ticket.setBookingEndtime(NOW.plusHours(60));
        ticket.setAutomaticPricingEnabled(true);
        return ticket;
    }

    @ParameterizedTest
    @CsvSource({"0,45000", "9,45000", "10,47500", "15,47500", "16,49000", "19,49000", "20,50000", "100,50000"})
    void discountsAccordingToObservedQuantity(long sold, int expected) {
        assertThat(TicketPricingService.calculateNextPrice(ticket(), sold, NOW)).isEqualTo(expected);
    }

    @Test
    void priceNeverCrossesFloorAndSmallPricesStillDecrease() {
        var ticket = ticket();
        ticket.setPrice(20001);
        assertThat(TicketPricingService.calculateNextPrice(ticket, 0, NOW)).isEqualTo(20000);
        ticket.setMinPrice(1);
        ticket.setPrice(2);
        assertThat(TicketPricingService.calculateNextPrice(ticket, 0, NOW)).isEqualTo(1);
        ticket.setPrice(Integer.MAX_VALUE);
        assertThat(TicketPricingService.calculateNextPrice(ticket, 0, NOW)).isEqualTo(1932735282);
    }

    @Test
    void lateSlowSalesUseFloorButFastSalesKeepPrice() {
        var ticket = ticket();
        ticket.setBookingEndtime(NOW.plusHours(2));
        assertThat(TicketPricingService.calculateNextPrice(ticket, 1, NOW)).isEqualTo(20000);
        assertThat(TicketPricingService.calculateNextPrice(ticket, 600, NOW)).isEqualTo(50000);
    }

    @Test
    void dueAdjustmentPersistsNewPriceAndEvaluationTime() {
        var ticket = ticket();
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(orders.sumPaidQuantity(1L, NOW.minusHours(2), NOW)).thenReturn(8L);
        service.adjustPrice(1L, NOW);
        assertThat(ticket.getPrice()).isEqualTo(45000);
        assertThat(ticket.getLastPriceEvaluatedAt()).isEqualTo(NOW);
        var saved = org.mockito.ArgumentCaptor.forClass(TicketPriceHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getPrice()).isEqualTo(45000);
        assertThat(saved.getValue().getChangedAt()).isEqualTo(NOW);
        service.adjustPrice(1L, NOW.plusMinutes(1));
        verify(history, times(1)).save(any());
    }

    @Test
    void sufficientSalesStillAdvanceEvaluationTimeWithoutHistory() {
        var ticket = ticket();
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(orders.sumPaidQuantity(1L, NOW.minusHours(2), NOW)).thenReturn(20L);
        service.adjustPrice(1L, NOW);
        assertThat(ticket.getPrice()).isEqualTo(50000);
        assertThat(ticket.getLastPriceEvaluatedAt()).isEqualTo(NOW);
        verifyNoInteractions(history);
    }

    @Test
    void disabledClosedSoldOutAndNotDueTicketsDoNotReadSales() {
        var ticket = ticket();
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        ticket.setAutomaticPricingEnabled(false);
        service.adjustPrice(1L, NOW);
        ticket.setAutomaticPricingEnabled(true);
        ticket.setBookingEndtime(NOW);
        service.adjustPrice(1L, NOW);
        ticket.setBookingEndtime(NOW.plusHours(60));
        ticket.setSold_ticket(1000);
        service.adjustPrice(1L, NOW);
        ticket.setSold_ticket(400);
        ticket.setSalesStartAt(NOW.minusMinutes(119));
        service.adjustPrice(1L, NOW);
        verifyNoInteractions(orders, history);
    }

    @Test
    void allRemainingStockHeldForCheckoutDoesNotTriggerDiscount() {
        var ticket = ticket();
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(orders.sumActiveQuantity(eq(1L), anyList(), eq(NOW))).thenReturn(600L);
        service.adjustPrice(1L, NOW);
        assertThat(ticket.getPrice()).isEqualTo(50000);
        verify(orders, never()).sumPaidQuantity(anyLong(), any(), any());
        verifyNoInteractions(history);
    }

    @Test
    void firstAutomaticConfigurationStoresBaselineAndStartsObservation() {
        var ticket = ticket();
        ticket.setInitialPrice(null);
        ticket.setMinPrice(null);
        ticket.setSalesStartAt(null);
        ticket.setAutomaticPricingEnabled(false);
        when(tickets.findManagedTicketForUpdate(1L, 10L)).thenReturn(Optional.of(ticket));
        service.configure(10L, 1L, new TicketPriceSettingsRequest(60000, 20000, true, null));
        assertThat(ticket.getInitialPrice()).isEqualTo(60000);
        assertThat(ticket.getPrice()).isEqualTo(60000);
        assertThat(ticket.getMinPrice()).isEqualTo(20000);
        assertThat(ticket.isAutomaticPricingEnabled()).isTrue();
        assertThat(ticket.getSalesStartAt()).isNotNull();
        var saved = org.mockito.ArgumentCaptor.forClass(TicketPriceHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getPrice()).isEqualTo(60000);
    }

    @Test
    void unauthorizedInvalidRangeAndInitialPriceChangesAreRejected() {
        when(tickets.findManagedTicketForUpdate(1L, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.configure(99L, 1L,
                new TicketPriceSettingsRequest(50000, 20000, true, null))).isInstanceOf(ApiException.class);
        var ticket = ticket();
        when(tickets.findManagedTicketForUpdate(1L, 10L)).thenReturn(Optional.of(ticket));
        assertThatThrownBy(() -> service.configure(10L, 1L,
                new TicketPriceSettingsRequest(50000, 60000, true, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.configure(10L, 1L,
                new TicketPriceSettingsRequest(60000, 20000, true, null))).isInstanceOf(ApiException.class);
        assertThat(ticket.getPrice()).isEqualTo(50000);
        verifyNoInteractions(history);
    }

    @Test
    void disablingAutomaticPricingWithoutManualEditKeepsLatestLockedPrice() {
        var ticket = ticket();
        ticket.setPrice(45000);
        when(tickets.findManagedTicketForUpdate(1L, 10L)).thenReturn(Optional.of(ticket));
        when(history.existsByTicketId(1L)).thenReturn(true);
        var response = service.configure(10L, 1L, new TicketPriceSettingsRequest(50000, 20000, false, null));
        assertThat(ticket.isAutomaticPricingEnabled()).isFalse();
        assertThat(response.price()).isEqualTo(45000);
        verify(history, never()).save(any());
    }

    @Test
    void zeroFloorAndManualPriceAreRejectedBecauseCheckoutRequiresPositiveAmounts() {
        var ticket = ticket();
        when(tickets.findManagedTicketForUpdate(1L, 10L)).thenReturn(Optional.of(ticket));
        assertThatThrownBy(() -> service.configure(10L, 1L,
                new TicketPriceSettingsRequest(50000, 0, true, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.configure(10L, 1L,
                new TicketPriceSettingsRequest(50000, 20000, false, 0))).isInstanceOf(ApiException.class);
        assertThat(ticket.getPrice()).isEqualTo(50000);
        verifyNoInteractions(history);
    }
}
