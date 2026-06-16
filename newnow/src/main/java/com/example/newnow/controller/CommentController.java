package com.example.newnow.controller;

import com.example.newnow.model.Comment;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.CommentService;
import com.example.newnow.service.LocationReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "*")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private LocationReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/review/{reviewId}")
    public ResponseEntity<?> addCommentToReview(
            @PathVariable Long reviewId,
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            LocationReview review = reviewService.findById(reviewId)
                    .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));

            if (user.getRole() != Role.MANAGER ||
                    review.getLocation().getManager() == null ||
                    !review.getLocation().getManager().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžer lokacije može odgovarati na utiske!"
                ));
            }

            String text = payload.get("text");
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Tekst komentara je obavezan!"
                ));
            }

            Comment comment = new Comment();
            comment.setText(text);
            comment.setReview(review);
            comment.setUser(user);
            comment.setParentComment(null); // Prvi nivo (odgovor na utisak)

            Comment saved = commentService.save(comment);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    //user odgovara na komentar menadzera
    @PostMapping("/{commentId}/reply")
    public ResponseEntity<?> replyToComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            //Nadjem parent
            Comment parentComment = commentService.findById(commentId)
                    .orElseThrow(() -> new RuntimeException("Komentar nije pronađen"));

            String text = payload.get("text");
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Tekst komentara je obavezan!"
                ));
            }

            Comment reply = new Comment();
            reply.setText(text);
            reply.setReview(parentComment.getReview());
            reply.setUser(user);
            reply.setParentComment(parentComment);

            Comment saved = commentService.save(reply);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    //Svi komentari
    @GetMapping("/review/{reviewId}")
    public ResponseEntity<?> getCommentsForReview(@PathVariable Long reviewId) {
        try {
            List<Comment> comments = commentService.findByReviewId(reviewId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<?> getRepliesForComment(@PathVariable Long commentId) {
        try {
            List<Comment> replies = commentService.findRepliesByParentId(commentId);
            return ResponseEntity.ok(replies);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            Comment comment = commentService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Komentar nije pronađen"));

            boolean isAuthor = comment.getUser().getId().equals(user.getId());
            boolean isManager = user.getRole() == Role.MANAGER &&
                    comment.getReview().getLocation().getManager() != null &&
                    comment.getReview().getLocation().getManager().getId().equals(user.getId());

            if (!isAuthor && !isManager) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo autor komentara ili menadžer lokacije može obrisati komentar!"
                ));
            }

            commentService.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Komentar obrisan ✅"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}