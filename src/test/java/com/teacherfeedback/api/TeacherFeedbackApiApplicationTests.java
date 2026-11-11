package com.teacherfeedback.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "auth.require-teacher-token=false")
class TeacherFeedbackApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
