package dev.li2fox.vibepetcore.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private final List<CoreModule> modules = new ArrayList<>();
    private final PetDebugLogger debugLogger;

    public ModuleManager(PetDebugLogger debugLogger) {
        this.debugLogger = debugLogger;
    }

    public void register(CoreModule module) {
        modules.add(module);
    }

    public void enableAll() {
        for (CoreModule module : modules) {
            runSafely(module, "enable", module::enable);
        }
    }

    public void reloadAll() {
        for (CoreModule module : modules) {
            runSafely(module, "reload", module::reload);
        }
    }

    public void disableAll() {
        List<CoreModule> reversed = new ArrayList<>(modules);
        Collections.reverse(reversed);
        for (CoreModule module : reversed) {
            runSafely(module, "disable", module::disable);
        }
    }

    private void runSafely(CoreModule module, String phase, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            String moduleName = module.getClass().getSimpleName();
            if (debugLogger != null) {
                debugLogger.errorRateLimited(
                    "module-manager-" + phase + "-" + moduleName,
                    "module-manager",
                    "Module " + moduleName + " failed during " + phase,
                    exception,
                    60_000L
                );
            }
        }
    }
}
