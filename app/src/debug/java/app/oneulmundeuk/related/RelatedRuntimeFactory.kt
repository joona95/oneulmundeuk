package app.oneulmundeuk.related

import android.app.Application

/**
 * Debug: the fake models when the flag file exists (see [DebugRelatedRuntime]), otherwise the same real runtime as
 * release ([productionRelatedRuntime]) — so a debug build can exercise real e5 with models installed on the device.
 */
fun createRelatedRuntime(app: Application, env: SemanticEnvironment): RelatedRuntime =
    if (DebugRelatedRuntime.isOn(app)) DebugRelatedRuntime(app, env.database) else productionRelatedRuntime(env)
