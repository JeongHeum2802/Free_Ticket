package wamddu.backend.eventDirector.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wamddu.backend.eventDirector.domain.EventDirector;

public interface EventDirectorRepository extends JpaRepository<EventDirector, Long> {
    boolean existsByUserId(Long userId);
}
