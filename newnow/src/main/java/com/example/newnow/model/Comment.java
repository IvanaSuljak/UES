package com.example.newnow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 🟢 VEZA SA UTISKOM (na koji utisak je komentar)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    @JsonIgnoreProperties({"comments", "location", "event", "hibernateLazyInitializer", "handler"})
    private LocationReview review;

    // 🟢 KO JE NAPISAO KOMENTAR
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "enabled", "email"})
    private User user;

    // 🟢 ODGOVOR NA DRUGI KOMENTAR (za thread komentara)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @JsonIgnoreProperties({"parentComment", "review", "hibernateLazyInitializer", "handler"})
    private Comment parentComment;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}