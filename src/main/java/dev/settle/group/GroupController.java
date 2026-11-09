package dev.settle.group;

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
@RequestMapping("/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public GroupResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<MembershipResponse> addMember(
            @PathVariable UUID id, @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(201).body(service.addMember(id, request));
    }

    @GetMapping("/{id}/members")
    public List<MembershipResponse> listMembers(@PathVariable UUID id) {
        return service.listMembers(id);
    }
}
