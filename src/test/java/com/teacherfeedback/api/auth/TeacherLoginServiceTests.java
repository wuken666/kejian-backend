package com.teacherfeedback.api.auth;

import com.teacherfeedback.api.dao.TeacherLoginSessionRepository;
import com.teacherfeedback.api.dao.TeacherRepository;
import com.teacherfeedback.api.dao.TeacherWechatBindingRepository;
import com.teacherfeedback.api.model.Student;
import com.teacherfeedback.api.model.Teacher;
import com.teacherfeedback.api.model.TeacherLoginSession;
import com.teacherfeedback.api.model.TeacherWechatBinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class TeacherLoginServiceTests {
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private TeacherWechatBindingRepository bindingRepository;
    @Mock
    private TeacherLoginSessionRepository sessionRepository;
    @Mock
    private WechatCodeExchangeClient wechatClient;

    @AfterEach
    void clearTeacherContext() {
        TeacherContext.clear();
    }

    @Test
    void createsOpaqueSessionAndNeverStoresRawToken() {
        Teacher teacher = new Teacher("测试老师");
        TeacherWechatBinding binding = new TeacherWechatBinding(
            teacher,
            "wx-test",
            "openid-test",
            null
        );
        when(wechatClient.exchange("one-time-code"))
            .thenReturn(new WechatCodeExchangeClient.WechatIdentity("wx-test", "openid-test", null));
        when(bindingRepository.findByAppidAndOpenid("wx-test", "openid-test"))
            .thenReturn(Optional.of(binding));
        when(sessionRepository.save(any(TeacherLoginSession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().login(" one-time-code ");

        assertTrue(response.accessToken().length() >= 40);
        assertNotNull(response.expiresAt());
        assertEquals("测试老师", response.teacher().name());
        ArgumentCaptor<TeacherLoginSession> captor = ArgumentCaptor.forClass(TeacherLoginSession.class);
        verify(sessionRepository).save(captor.capture());
        assertNotEquals(response.accessToken(), captor.getValue().getTokenHash());
        assertEquals(
            TeacherLoginService.sha256(response.accessToken()),
            captor.getValue().getTokenHash()
        );
    }

    @Test
    void rejectsAStudentBelongingToAnotherTeacher() {
        TeacherContext.set(7L);
        Student student = new Student(8L, "小雨", "初二", "英语", "巩固语法");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> TeacherContext.assertStudentAccess(student)
        );

        assertEquals(NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void logoutRevokesTheServerSession() {
        Teacher teacher = new Teacher("测试老师");
        TeacherLoginSession session = new TeacherLoginSession(
            teacher,
            TeacherLoginService.sha256("token-value"),
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusHours(1)
        );
        when(sessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            eq(TeacherLoginService.sha256("token-value")),
            any(LocalDateTime.class)
        )).thenReturn(Optional.of(session));

        service().logout("Bearer token-value");

        verify(sessionRepository).save(session);
        assertTrue(!session.isActiveAt(LocalDateTime.now()));
    }

    private TeacherLoginService service() {
        return new TeacherLoginService(
            teacherRepository,
            bindingRepository,
            sessionRepository,
            wechatClient,
            168
        );
    }

    @Test
    void profileUpdateUsesCurrentTeacherAndPersistsCompletion() {
        TeacherContext.set(7L);
        Teacher teacher = new Teacher("微信老师");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));
        var result = service().updateProfile(new com.teacherfeedback.api.dto.AuthDtos.UpdateProfileRequest(" 刘老师 ", null));
        assertEquals("刘老师", result.name());
        assertTrue(result.profileCompleted());
        assertEquals("", result.avatarData());
        assertThrows(ResponseStatusException.class, () -> service().updateProfile(new com.teacherfeedback.api.dto.AuthDtos.UpdateProfileRequest("刘老师", "https://untrusted.example/avatar.svg")));
        assertEquals("", teacher.getAvatarData());
    }

    @Test
    void firstLoginCreatesTeacherAndRepeatLoginReusesBinding() {
        Teacher teacher = new Teacher("微信老师");
        when(wechatClient.exchange("new-code")).thenReturn(new WechatCodeExchangeClient.WechatIdentity("wx-test", "new-openid", null));
        when(bindingRepository.findByAppidAndOpenid("wx-test", "new-openid")).thenReturn(Optional.empty());
        when(teacherRepository.save(any(Teacher.class))).thenReturn(teacher);
        service().login("new-code");
        ArgumentCaptor<TeacherWechatBinding> binding = ArgumentCaptor.forClass(TeacherWechatBinding.class);
        verify(bindingRepository).save(binding.capture());
        when(bindingRepository.findByAppidAndOpenid("wx-test", "new-openid")).thenReturn(Optional.of(binding.getValue()));
        service().login("new-code");
        verify(teacherRepository, org.mockito.Mockito.times(1)).save(any(Teacher.class));
    }
}
