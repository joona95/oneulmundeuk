package app.oneulmundeuk.related

import android.app.Application

/**
 * Release: the real runtime ([productionRelatedRuntime]). It does nothing until `SemanticGate` opens (관련된 생각 ON +
 * models Ready), shows no results yet (no judge), and never creates fake results.
 */
@Suppress("UNUSED_PARAMETER")
fun createRelatedRuntime(app: Application, env: SemanticEnvironment): RelatedRuntime = productionRelatedRuntime(env)
