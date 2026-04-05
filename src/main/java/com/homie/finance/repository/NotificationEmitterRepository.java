package com.homie.finance.repository;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationEmitterRepository {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void add(String userId, SseEmitter emitter) {
        emitters.put(userId, emitter);
    }

    public void remove(String userId) {
        emitters.remove(userId);
    }

    public SseEmitter get(String userId) {
        return emitters.get(userId);
    }
}
