package de.ultrabuild.trainsounds.logic;

public interface EngineToggleCarrier {

    boolean trainsounds$isEngineBuiltIn();

    void trainsounds$setEngineBuiltIn(boolean enabled);

    default void trainsounds$toggleEngineBuiltIn() {
        trainsounds$setEngineBuiltIn(!trainsounds$isEngineBuiltIn());
    }
}

