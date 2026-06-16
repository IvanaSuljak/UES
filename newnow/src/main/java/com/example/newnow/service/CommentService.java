package com.example.newnow.service;

import com.example.newnow.model.Comment;
import java.util.List;
import java.util.Optional;

public interface CommentService {
    Comment save(Comment comment);
    Optional<Comment> findById(Long id);
    List<Comment> findByReviewId(Long reviewId);
    List<Comment> findRepliesByParentId(Long parentId);
    void deleteById(Long id);
}