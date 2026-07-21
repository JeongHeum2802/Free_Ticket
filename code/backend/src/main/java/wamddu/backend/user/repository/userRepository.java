package wamddu.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wamddu.backend.user.domain.User;

@Repository
public interface userRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByPhonenumber(String phonenumber);
    boolean existsByUsername(String username);
    User findByEmail(String email);
}
