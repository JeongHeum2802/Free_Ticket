package wamddu.backend.chat;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_GEMINI_SMOKE", matches = "true")
class GeminiLiveSmokeTest {
    private GeminiSearchClient client() {
        var env = Dotenv.configure().ignoreIfMissing().load();
        String key = env.get("GEMINI_API_KEY", "");
        assertThat(key.isBlank()).as("GEMINI_API_KEY must be configured (value hidden)").isFalse();
        return new GeminiSearchClient("https://generativelanguage.googleapis.com", key,
                env.get("GEMINI_MODEL", "gemini-3.1-flash-lite"));
    }

    @Test void realApiExtractsSyntheticSearchWithProductionClient() {
        var result = client().extract(List.of("2026년 11월 7일 서울에서 볼 수 있는 5만원 이하 뮤지컬 찾아줘"));
        assertThat(result.search()).isTrue();
        assertThat(result.category()).isEqualTo("musical");
        assertThat(result.region()).isEqualTo("서울");
        assertThat(result.maxPrice()).isEqualTo(50000);
        assertThat(result.dateFrom()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(result.dateTo()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test void exhibitionSearchDoesNotInventDatesOrAvailabilityButHonorsExplicitFollowUp() {
        var client = client();
        var general = client.extract(List.of("서울 전시 찾아줘"));
        assertThat(general.search()).isTrue();
        assertThat(general.category()).isEqualTo("exhibition");
        assertThat(general.region()).isEqualTo("서울");
        assertThat(general.title()).isNull();
        assertThat(general.dateFrom()).isNull();
        assertThat(general.dateTo()).isNull();
        assertThat(general.maxPrice()).isNull();
        assertThat(general.availableOnly()).isNull();
        var bookable = client.extract(List.of("서울 전시 찾아줘", "예매 가능한 것만"));
        assertThat(bookable.search()).isTrue();
        assertThat(bookable.category()).isEqualTo("exhibition");
        assertThat(bookable.region()).isEqualTo("서울");
        assertThat(bookable.availableOnly()).isTrue();
    }
}
