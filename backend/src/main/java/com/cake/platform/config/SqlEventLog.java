package com.cake.platform.config;

import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development SQL execution events only. Raw SQL, parameters, results and exceptions stay private. */
public final class SqlEventLog implements Log {
    private static final Logger LOG = LoggerFactory.getLogger("com.cake.platform.sql.events");

    public SqlEventLog(String ignoredStatementName) {
    }

    @Override public boolean isDebugEnabled() { return LOG.isDebugEnabled(); }
    @Override public boolean isTraceEnabled() { return false; }
    @Override public void error(String message, Throwable exception) { LOG.warn("sqlEvent=ERROR"); }
    @Override public void error(String message) { LOG.warn("sqlEvent=ERROR"); }
    @Override public void warn(String message) { LOG.warn("sqlEvent=WARN"); }
    @Override public void trace(String message) { }

    @Override
    public void debug(String message) {
        if (message != null && message.startsWith("==>  Preparing:")) {
            LOG.debug("sqlEvent=PREPARE");
        }
    }
}
