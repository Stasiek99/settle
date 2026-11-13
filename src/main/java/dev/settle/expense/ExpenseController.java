package dev.settle.expense;

import dev.settle.common.PageQuery;
import dev.settle.common.ResourceLocations;
import dev.settle.common.PageResponse;
import dev.settle.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService service;
    private final ResourceLocations locations;

    public ExpenseController(ExpenseService service, ResourceLocations locations) {
        this.service = service;
        this.locations = locations;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @PathVariable UUID groupId, @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponse created = service.create(groupId, request);
        var location = locations.ofChild(created.id());
        return ResponseEntity.created(location).eTag(String.valueOf(created.version())).body(created);
    }

    @GetMapping
    public PageResponse<ExpenseResponse> list(@PathVariable UUID groupId, @Valid @ParameterObject PageQuery page) {
        return service.list(groupId, page);
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> get(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
        ExpenseResponse expense = service.get(groupId, expenseId);
        return ResponseEntity.ok().eTag(String.valueOf(expense.version())).body(expense);
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody CreateExpenseRequest request,
            CurrentUser user) {
        ExpenseResponse updated = service.update(groupId, expenseId, user.id(), ifMatch, request);
        return ResponseEntity.ok().eTag(String.valueOf(updated.version())).body(updated);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            CurrentUser user) {
        service.delete(groupId, expenseId, user.id(), ifMatch);
        return ResponseEntity.noContent().build();
    }
}
