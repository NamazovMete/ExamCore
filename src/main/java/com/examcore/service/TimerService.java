package com.examcore.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TimerService {

    private static final List<Long> DEFAULT_ALERT_THRESHOLDS = List.of(300L, 60L, 10L);

    public interface TimerListener {
        void onTick(long secondsRemaining);

        void onAlert(long secondsRemaining);

        void onExpired();
    }

    private final ScheduledExecutorService scheduler;
    private final AtomicLong secondsRemaining = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<Long> alertThresholds = new ConcurrentSkipListSet<>(DEFAULT_ALERT_THRESHOLDS);

    private volatile TimerListener listener;
    private ScheduledFuture<?> tickFuture;

    public TimerService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TimerService-Countdown");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setListener(TimerListener listener) {
        this.listener = listener;
    }

    public void setAlertThresholds(Set<Long> thresholdsInSeconds) {
        if (thresholdsInSeconds == null) {
            throw new IllegalArgumentException("Thresholds cannot be null");
        }
        alertThresholds.clear();
        alertThresholds.addAll(thresholdsInSeconds);
    }

    public synchronized void start(int durationLimitMinutes) {
        if (durationLimitMinutes <= 0) {
            throw new IllegalArgumentException("Duration limit must be positive");
        }
        startInternal(durationLimitMinutes * 60L);
    }

    public synchronized void startSeconds(long durationSeconds) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        startInternal(durationSeconds);
    }

    private void startInternal(long totalSeconds) {
        if (running.get()) {
            throw new IllegalStateException("Timer is already running");
        }
        if (scheduler.isShutdown()) {
            throw new IllegalStateException("TimerService has been shut down");
        }
        secondsRemaining.set(totalSeconds);
        running.set(true);
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        if (!running.get()) {
            return;
        }
        long remaining = secondsRemaining.decrementAndGet();
        TimerListener currentListener = listener;

        if (remaining <= 0) {
            expireOnce(currentListener);
            return;
        }

        if (alertThresholds.contains(remaining) && currentListener != null) {
            currentListener.onAlert(remaining);
        }
        if (currentListener != null) {
            currentListener.onTick(Math.max(remaining, 0));
        }
    }

    private synchronized void expireOnce(TimerListener currentListener) {
        if (running.compareAndSet(true, false)) {
            secondsRemaining.set(0);
            cancelTickFuture();
            if (currentListener != null) {
                currentListener.onExpired();
            }
        }
    }

    public synchronized void stop() {
        running.set(false);
        cancelTickFuture();
    }

    private void cancelTickFuture() {
        if (tickFuture != null) {
            tickFuture.cancel(false);
            tickFuture = null;
        }
    }

    public long getSecondsRemaining() {
        return secondsRemaining.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}
