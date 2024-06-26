package edu.just.mashoora.repository;

import edu.just.mashoora.components.Comment;
import edu.just.mashoora.components.CommentVote;
import edu.just.mashoora.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentVoteRepository extends JpaRepository<CommentVote, Long> {

    @Query("SELECT COUNT(cv) FROM CommentVote cv WHERE cv.comment.id = :commentId AND cv.vote = true")
    int countByCommentIdAndVoteTrue(@Param("commentId") Long commentId);

    @Query("SELECT COUNT(cv) FROM CommentVote cv WHERE cv.comment.id = :commentId AND cv.vote = false")
    int countByCommentIdAndVoteFalse(@Param("commentId") Long commentId);
    int countByIdAndVoteTrue(Long commentId);
    int countByIdAndVoteFalse(Long commentId);
    Optional<CommentVote> findByUserAndComment(User user, Comment comment);

}
