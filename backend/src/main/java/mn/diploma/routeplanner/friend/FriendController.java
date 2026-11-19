package mn.diploma.routeplanner.friend;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.friend.FriendService.FriendshipDto;
import mn.diploma.routeplanner.friend.FriendService.InviteLink;
import mn.diploma.routeplanner.friend.FriendService.UserRef;

/** Friend module API (design table "Хэрэглэгч ба нууцлалын модулиудын API"). */
@RestController
public class FriendController {

    public record FriendRequest(@NotBlank String username) {
    }

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping("/api/friends")
    public List<FriendshipDto> list(@AuthenticationPrincipal Jwt jwt) {
        return friends.list(JwtService.userId(jwt));
    }

    @PostMapping("/api/friends/requests")
    public ResponseEntity<FriendshipDto> request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FriendRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friends.request(JwtService.userId(jwt), req.username()));
    }

    @PostMapping("/api/friends/requests/{id}/accept")
    public FriendshipDto accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return friends.respond(JwtService.userId(jwt), id, true);
    }

    @PostMapping("/api/friends/requests/{id}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        friends.respond(JwtService.userId(jwt), id, false);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/friends/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        friends.remove(JwtService.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/friends/invite-link")
    public InviteLink inviteLink(@AuthenticationPrincipal Jwt jwt) {
        return friends.inviteLink(JwtService.userId(jwt));
    }

    /** Who invited me (public, so the page can show it before login). */
    @GetMapping("/api/friends/invites/{token}")
    public UserRef inviter(@PathVariable String token) {
        return friends.inviter(token);
    }

    @PostMapping("/api/friends/invites/{token}/accept")
    public FriendshipDto acceptInvite(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        return friends.acceptInvite(JwtService.userId(jwt), token);
    }
}
