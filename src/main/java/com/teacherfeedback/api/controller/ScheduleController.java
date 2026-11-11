package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.dto.ScheduleDtos.Response;
import com.teacherfeedback.api.dto.ScheduleDtos.SaveRequest;
import com.teacherfeedback.api.dto.ScheduleDtos.StatusRequest;
import com.teacherfeedback.api.model.ScheduledLesson;
import com.teacherfeedback.api.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Response> list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.list(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody SaveRequest request) {
        return service.create(request);
    }

    @GetMapping("/overdue")
    public List<Response> overdue() { return service.overdue(); }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody SaveRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public Response status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.status(id, ScheduledLesson.Status.valueOf(request.status()));
    }
}
