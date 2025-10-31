package org.learningjava.bmtool1.infrastructure.adapter.in.web.admin;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobRegistry {

    public enum JobState { RUNNING, DONE, FAILED }

    public record JobStatus(
            String id,
            String type,
            JobState state,
            String message,
            int processed,
            int total
    ) {}

    private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

    public String start(String type, int total) {
        String id = UUID.randomUUID().toString();
        jobs.put(id, new JobStatus(id, type, JobState.RUNNING, "Started", 0, Math.max(total, 0)));
        return id;
    }

    public void update(String id, int processed, String message) {
        jobs.compute(id, (k, j) -> {
            JobStatus cur = j != null ? j : new JobStatus(id, "RAG", JobState.RUNNING, "Started", 0, 0);
            return new JobStatus(id, cur.type(), JobState.RUNNING, message != null ? message : cur.message(),
                    processed, cur.total());
        });
    }

    public void done(String id, String message) {
        jobs.compute(id, (k, j) -> {
            JobStatus cur = j != null ? j : new JobStatus(id, "RAG", JobState.RUNNING, "Started", 0, 0);
            int total = cur.total();
            return new JobStatus(id, cur.type(), JobState.DONE, message != null ? message : "Done", total, total);
        });
    }

    public void fail(String id, String message) {
        jobs.compute(id, (k, j) -> {
            JobStatus cur = j != null ? j : new JobStatus(id, "RAG", JobState.RUNNING, "Started", 0, 0);
            return new JobStatus(id, cur.type(), JobState.FAILED, message != null ? message : "Failed",
                    cur.processed(), cur.total());
        });
    }

    public JobStatus get(String id) {
        return jobs.get(id);
    }
}
