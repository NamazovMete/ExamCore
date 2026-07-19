package com.examcore.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerServiceTest {

    private final TimerService timerService = new TimerService();

    @AfterEach
    void tearDown() {
        timerService.shutdown();
    }

    @Test
    void start_setsSecondsRemainingFromMinutes() {
        timerService.start(2);

        assertEquals(120, timerService.getSecondsRemaining());
        assertTrue(timerService.isRunning());
    }

    @Test
    void start_zeroOrNegativeDuration_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> timerService.start(0));
        assertThrows(IllegalArgumentException.class, () -> timerService.start(-5));
    }

    @Test
    void start_whileAlreadyRunning_throwsIllegalStateException() {
        timerService.start(5);

        assertThrows(IllegalStateException.class, () -> timerService.start(5));
    }

    @Test
    void tick_decrementsSecondsRemaining() throws InterruptedException {
        CountDownLatch tickedAtLeastTwice = new CountDownLatch(2);
        timerService.setListener(new TimerService.TimerListener() {
            @Override
            public void onTick(long secondsRemaining) {
                tickedAtLeastTwice.countDown();
            }

            @Override
            public void onAlert(long secondsRemaining) {
            }

            @Override
            public void onExpired() {
            }
        });

        timerService.startSeconds(10);
        boolean completed = tickedAtLeastTwice.await(5, TimeUnit.SECONDS);

        assertTrue(completed, "Expected at least two onTick callbacks within 5 seconds");
        assertTrue(timerService.getSecondsRemaining() < 10);
    }

    @Test
    void expiry_firesOnExpiredExactlyOnceAndStopsTimer() throws InterruptedException {
        AtomicInteger expiredCount = new AtomicInteger(0);
        CountDownLatch expiredLatch = new CountDownLatch(1);
        timerService.setListener(new TimerService.TimerListener() {
            @Override
            public void onTick(long secondsRemaining) {
            }

            @Override
            public void onAlert(long secondsRemaining) {
            }

            @Override
            public void onExpired() {
                expiredCount.incrementAndGet();
                expiredLatch.countDown();
            }
        });

        timerService.startSeconds(1);
        boolean expired = expiredLatch.await(5, TimeUnit.SECONDS);

        assertTrue(expired, "Timer should have expired within 5 seconds");
        // give any stray extra tick a moment to (not) arrive
        Thread.sleep(200);
        assertEquals(1, expiredCount.get());
        assertFalse(timerService.isRunning());
        assertEquals(0, timerService.getSecondsRemaining());
    }

    @Test
    void expiry_midTest_triggersAutomatedSubmissionCallback() throws InterruptedException {
        AtomicBoolean autoSubmitted = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        timerService.setListener(new TimerService.TimerListener() {
            @Override
            public void onTick(long secondsRemaining) {
            }

            @Override
            public void onAlert(long secondsRemaining) {
            }

            @Override
            public void onExpired() {
                autoSubmitted.set(true);
                latch.countDown();
            }
        });

        timerService.startSeconds(1);
        latch.await(5, TimeUnit.SECONDS);

        assertTrue(autoSubmitted.get(), "Timer expiry should trigger the auto-submit callback");
    }

    @Test
    void alertThresholds_fireAtConfiguredSecondsRemaining() throws InterruptedException {
        timerService.setAlertThresholds(Set.of(3L));
        AtomicLong alertedAt = new AtomicLong(-1);
        CountDownLatch alertLatch = new CountDownLatch(1);
        timerService.setListener(new TimerService.TimerListener() {
            @Override
            public void onTick(long secondsRemaining) {
            }

            @Override
            public void onAlert(long secondsRemaining) {
                alertedAt.set(secondsRemaining);
                alertLatch.countDown();
            }

            @Override
            public void onExpired() {
            }
        });

        timerService.startSeconds(4);
        boolean alerted = alertLatch.await(5, TimeUnit.SECONDS);

        assertTrue(alerted, "Expected an alert callback at the configured threshold");
        assertEquals(3L, alertedAt.get());
    }

    @Test
    void stop_beforeExpiry_doesNotFireOnExpired() throws InterruptedException {
        AtomicBoolean expired = new AtomicBoolean(false);
        timerService.setListener(new TimerService.TimerListener() {
            @Override
            public void onTick(long secondsRemaining) {
            }

            @Override
            public void onAlert(long secondsRemaining) {
            }

            @Override
            public void onExpired() {
                expired.set(true);
            }
        });

        timerService.startSeconds(2);
        timerService.stop();
        Thread.sleep(2500);

        assertFalse(expired.get());
        assertFalse(timerService.isRunning());
    }

    @Test
    void stop_isIdempotentWhenNotRunning() {
        timerService.stop();
        timerService.stop();

        assertFalse(timerService.isRunning());
    }

    @Test
    void startSeconds_zeroOrNegative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> timerService.startSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> timerService.startSeconds(-1));
    }

    @Test
    void setAlertThresholds_nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> timerService.setAlertThresholds(null));
    }

    @Test
    void start_afterShutdown_throwsIllegalStateException() {
        timerService.shutdown();

        assertThrows(IllegalStateException.class, () -> timerService.start(1));
    }

    @Test
    void restartAfterStop_worksCorrectly() throws InterruptedException {
        timerService.startSeconds(10);
        timerService.stop();

        timerService.startSeconds(5);

        assertTrue(timerService.isRunning());
        assertEquals(5, timerService.getSecondsRemaining());
    }
}
