package com.example.newnow.service_impl;

import com.example.newnow.model.Comment;
import com.example.newnow.repository.CommentRepository;
import com.example.newnow.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;

    @Autowired
    public CommentServiceImpl(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Override
    public Comment save(Comment comment) {
        return commentRepository.save(comment);
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return commentRepository.findById(id);
    }

    @Override
    public List<Comment> findByReviewId(Long reviewId) {
        return commentRepository.findByReviewId(reviewId);
    }

    @Override
    public List<Comment> findRepliesByParentId(Long parentId) {
        return commentRepository.findRepliesByParentId(parentId);
    }

    @Override
    public void deleteById(Long id) {
        commentRepository.deleteById(id);
    }
}