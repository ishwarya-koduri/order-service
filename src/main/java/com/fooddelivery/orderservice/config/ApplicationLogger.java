package com.fooddelivery.orderservice.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApplicationLogger {

    private final Logger logger;

    private ApplicationLogger(Class<?> clazz) {
        this.logger = LogManager.getLogger(clazz);
    }

    public static ApplicationLogger getLogger(Class<?> clazz) {
        return new ApplicationLogger(clazz);
    }

    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }

    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }

    public void error(String message, Object... args) {
        logger.error(message, args);
    }

    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}