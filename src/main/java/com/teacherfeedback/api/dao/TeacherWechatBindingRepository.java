package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.TeacherWechatBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherWechatBindingRepository extends JpaRepository<TeacherWechatBinding, Long> {
    Optional<TeacherWechatBinding> findByAppidAndOpenid(String appid, String openid);
}
