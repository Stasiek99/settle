package dev.settle.settlement;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/settlements")
public class SettlementController {

    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SettlementResponse> create(
            @PathVariable UUID groupId, @Valid @RequestBody CreateSettlementRequest request) {
        SettlementResponse created = service.create(groupId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<SettlementResponse> list(@PathVariable UUID groupId) {
        return service.list(groupId);
    }

    @GetMapping("/{settlementId}")
    public SettlementResponse get(@PathVariable UUID groupId, @PathVariable UUID settlementId) {
        return service.get(groupId, settlementId);
    }
}
