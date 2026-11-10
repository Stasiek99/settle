package dev.settle.group;

import dev.settle.auth.CurrentUser;
import dev.settle.common.ETags;
import dev.settle.common.PageQuery;
import dev.settle.common.PageResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        return ResponseEntity.created(location).eTag(String.valueOf(created.version())).body(created);
    }

    @GetMapping
    public PageResponse<GroupResponse> list(CurrentUser user, @Valid @ParameterObject PageQuery page) {
        return service.listFor(user.id(), page);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> get(@PathVariable UUID groupId) {
        GroupResponse group = service.get(groupId);
        return ResponseEntity.ok().eTag(String.valueOf(group.version())).body(group);
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<GroupResponse> rename(
            @PathVariable UUID groupId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateGroupRequest request,
            CurrentUser user) {
        access.requireOwner(groupId, user.id());
        GroupResponse updated = service.rename(groupId, ETags.requireVersion(ifMatch), request);
        return ResponseEntity.ok().eTag(String.valueOf(updated.version())).body(updated);
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
