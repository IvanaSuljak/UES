package com.example.newnow.repository;

import com.example.newnow.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Svi komentari
    @Query("SELECT c FROM Comment c WHERE c.review.id = :reviewId AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findByReviewId(@Param("reviewId") Long reviewId);

    // Odgovori na odredjeni kom
    @Query("SELECT c FROM Comment c WHERE c.parentComment.id = :parentId ORDER BY c.createdAt ASC")
    List<Comment> findRepliesByParentId(@Param("parentId") Long parentId);
}