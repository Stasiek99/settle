package dev.settle.expense;

import dev.settle.common.PageQuery;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @PathVariable UUID groupId, @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponse created = service.create(groupId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<ExpenseResponse> list(@PathVariable UUID groupId, @Valid @ParameterObject PageQuery page) {
        return service.list(groupId, page);
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse get(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
        return service.get(groupId, expenseId);
    }
}
