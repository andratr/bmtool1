package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobRegistry {
    private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

    public String start(String type, int total) {
        String id = UUID.randomUUID().toString();
        jobs.put(id, new JobStatus(id, type, "RUNNING", "Started", 0, total));
        return id;
    }

    public void update(String id, int processed, String message) {
        jobs.compute(id, (k, j) -> new JobStatus(id, j.type(), "RUNNING", message, processed, j.total()));
    }

    public void done(String id, String message) {
        jobs.compute(id, (k, j) -> new JobStatus(id, j.type(), "DONE", message, j.total(), j.total()));
    }

    public void fail(String id, String message) {
        jobs.compute(id, (k, j) -> new JobStatus(id, j.type(), "FAILED", message, j.processed(), j.total()));
    }

    public JobStatus get(String id) {
        return jobs.get(id);
    }

    public record JobStatus(String id, String type, String state, String message, int processed, int total) {
    }
}
