package dev.li2fox.vibepetcore.core;

public interface CoreModule {
    void enable();

    void disable();

    default void reload() {
        disable();
        enable();
    }
}
