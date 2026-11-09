package dev.settle.settlement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/settlements/suggestions")
public class SettlementSuggestionController {

    private final SettlementSuggestionService service;

    public SettlementSuggestionController(SettlementSuggestionService service) {
        this.service = service;
    }

    @GetMapping
    public SettlementSuggestionsResponse get(@PathVariable UUID groupId) {
        return service.forGroup(groupId);
    }
}
