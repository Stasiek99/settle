package dev.settle.settlement;

import dev.settle.common.PageQuery;
import dev.settle.common.ResourceLocations;
import dev.settle.common.PageResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/settlements")
public class SettlementController {

    private final SettlementService service;
    private final ResourceLocations locations;

    public SettlementController(SettlementService service, ResourceLocations locations) {
        this.service = service;
        this.locations = locations;
    }

    @PostMapping
    public ResponseEntity<SettlementResponse> create(
            @PathVariable UUID groupId, @Valid @RequestBody CreateSettlementRequest request) {
        SettlementResponse created = service.create(groupId, request);
        var location = locations.ofChild(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<SettlementResponse> list(@PathVariable UUID groupId, @Valid @ParameterObject PageQuery page) {
        return service.list(groupId, page);
    }

    @GetMapping("/{settlementId}")
    public SettlementResponse get(@PathVariable UUID groupId, @PathVariable UUID settlementId) {
        return service.get(groupId, settlementId);
    }
}
