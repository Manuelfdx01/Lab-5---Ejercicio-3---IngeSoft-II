package co.edu.unal.laboratorio5;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaludController {

    @GetMapping("/api/v1/salud")
    public Map<String, String> salud() {
        return Map.of("estado", "ok");
    }
}

