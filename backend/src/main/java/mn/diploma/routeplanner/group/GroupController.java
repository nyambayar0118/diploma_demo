package mn.diploma.routeplanner.group;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.group.GroupDtos.CreateGroupRequest;
import mn.diploma.routeplanner.group.GroupDtos.GroupView;
import mn.diploma.routeplanner.group.GroupDtos.JoinInfo;
import mn.diploma.routeplanner.group.GroupDtos.JoinRequest;
import mn.diploma.routeplanner.group.GroupDtos.JoinResult;
import mn.diploma.routeplanner.group.GroupDtos.ModifyMembersRequest;
import mn.diploma.routeplanner.group.GroupDtos.OptionsView;
import mn.diploma.routeplanner.group.GroupDtos.ShareRequest;

/** Бүлгийн модулийн API (зохиомжийн "Бүлгийн төлөвлөгөөний модулийн API" хүснэгт). */
@RestController
public class GroupController {

    private final GroupPlanningService groups;

    public GroupController(GroupPlanningService groups) {
        this.groups = groups;
    }

    @PostMapping("/api/groups")
    public ResponseEntity<GroupView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGroupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groups.createSession(JwtService.userId(jwt), req));
    }

    @GetMapping("/api/groups")
    public List<GroupView> list(@AuthenticationPrincipal Jwt jwt) {
        return groups.list(JwtService.userId(jwt));
    }

    @GetMapping("/api/groups/{id}")
    public GroupView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return groups.get(JwtService.userId(jwt), id);
    }

    @PatchMapping("/api/groups/{id}/members")
    public GroupView members(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                             @Valid @RequestBody ModifyMembersRequest req) {
        return groups.modifyMembers(JwtService.userId(jwt), id, req);
    }

    @GetMapping("/api/groups/{id}/options")
    public OptionsView options(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                               @RequestParam(defaultValue = "false") boolean again) {
        return groups.getOptions(JwtService.userId(jwt), id, again);
    }

    @PostMapping("/api/groups/{id}/share")
    public GroupView share(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ShareRequest req) {
        return groups.share(JwtService.userId(jwt), id, req.placeId());
    }

    @DeleteMapping("/api/groups/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        groups.cancel(JwtService.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Нийтэд нээлттэй: урилгын холбоос юуны тухай болохыг харуулна. */
    @GetMapping("/api/groups/join/{token}")
    public JoinInfo joinInfo(@PathVariable String token) {
        return groups.joinInfo(token);
    }

    /** Нийтэд нээлттэй: зочноор, нэвтэрсэн бол гишүүнээр нэгдэнэ. */
    @PostMapping("/api/groups/join/{token}")
    public JoinResult join(@AuthenticationPrincipal Jwt jwt, @PathVariable String token,
                           @Valid @RequestBody JoinRequest req) {
        return groups.join(token, jwt == null ? null : JwtService.userId(jwt), req);
    }
}
