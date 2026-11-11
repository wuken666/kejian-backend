package com.teacherfeedback.api.dao;

import com.teacherfeedback.api.model.ClientErrorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientErrorEventRepository extends JpaRepository<ClientErrorEvent, Long> {
}
