package com.example.estatex.analytics.engine;

import java.time.Instant;

/** Public access to the driver-independent timestamp conversion, for other analytics packages. */
public final class JobLockAccess {
    private JobLockAccess() {
    }

    public static Instant instant(Object v) {
        return JobLock.instant(v);
    }
}
