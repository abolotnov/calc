package org.sasha.exception;

public class ConfigLoadException extends RuntimeException{
    public ConfigLoadException(String message){
        super("Failed to load configuration: %s".formatted(message));
    }

    public ConfigLoadException(String message, Throwable cause){
        super("Failed to load configuration: %s".formatted(message), cause);
    }
}
