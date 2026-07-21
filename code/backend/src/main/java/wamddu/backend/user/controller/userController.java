package wamddu.backend.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.user.domain.signUpRequestDTO;
import wamddu.backend.user.service.userService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class userController {

    private final userService userService;

    @PostMapping("/api/auth/signup")
    public ResponseEntity<Map<String,Object>> signUp(@RequestBody signUpRequestDTO requestDTO) {
        return  userService.signUp(requestDTO);
    }

}
