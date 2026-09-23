package wamddu.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.global.response.ApiResponse;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.UserStatus;
import wamddu.backend.user.repository.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tables")
@RequiredArgsConstructor
public class AdminTableController {
    private final AdminTableService service;
    private final UserRepository userRepository;

    public record UpdateRequest(List<AdminTableService.RowChange> changes,
                                List<java.util.Map<String, String>> creations,
                                List<AdminTableService.RowDeletion> deletions) {}

    @PatchMapping("/{table}")
    public ApiResponse<AdminTableService.TablePage> update(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestBody UpdateRequest request) {
        requireAdmin(principal);
        return ApiResponse.success("변경 사항을 저장했습니다.", service.save(table, request.changes(), request.creations(), request.deletions(), page, size));
    }

    @GetMapping
    public ApiResponse<List<String>> tables(@AuthenticationPrincipal UserDetails principal) {
        requireAdmin(principal);
        return ApiResponse.success(service.tables());
    }

    @GetMapping("/{table}")
    public ApiResponse<AdminTableService.TablePage> read(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        requireAdmin(principal);
        return ApiResponse.success(service.read(table, page, size));
    }

    private void requireAdmin(UserDetails principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
        }
        // Recheck the current DB role so a revoked admin token cannot retain access.
        var user = userRepository.findById(Long.parseLong(principal.getUsername()));
        if (user.isEmpty() || user.get().getRole() != Role.ADMIN
                || user.get().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자만 조회할 수 있습니다.");
        }
    }
}
