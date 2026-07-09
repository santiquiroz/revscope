package com.revscope.core.obd.pid

/** Loads the real production PID registry JSON from the test classpath. */
object TestPids {
    fun load(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("pids_mode01.json")) {
            "pids_mode01.json not found on test classpath"
        }.bufferedReader().readText()
}
