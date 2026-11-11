package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.LessonSessionSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonSessionSegmentRepository extends JpaRepository<LessonSessionSegment, Long> {
    Optional<LessonSessionSegment> findByLessonSessionIdAndClientSegmentId(Long lessonSessionId, String clientSegmentId);
    List<LessonSessionSegment> findByLessonSessionIdOrderBySequenceNumberAsc(Long lessonSessionId);

    // 按课堂会话直接删除全部片段，并清理持久化缓存中的旧关联。
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LessonSessionSegment segment where segment.lessonSession.id = :sessionId")
    int deleteByLessonSessionId(@Param("sessionId") Long sessionId);
}
