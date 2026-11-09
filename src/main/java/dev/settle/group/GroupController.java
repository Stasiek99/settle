package dev.settle.group;

import dev.settle.auth.CurrentUser;
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
    private final GroupAccess access;

    public GroupController(GroupService service, GroupAccess access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request, CurrentUser user) {
        GroupResponse created = service.create(request, user.id());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<GroupResponse> list(CurrentUser user) {
        return service.listFor(user.id());
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable UUID groupId) {
        return service.get(groupId);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<MembershipResponse> addMember(
            @PathVariable UUID groupId, @Valid @RequestBody AddMemberRequest request, CurrentUser user) {
        access.requireOwner(groupId, user.id());
        return ResponseEntity.status(201).body(service.addMember(groupId, request));
    }

    @GetMapping("/{groupId}/members")
    public List<MembershipResponse> listMembers(@PathVariable UUID groupId) {
        return service.listMembers(groupId);
    }
}
