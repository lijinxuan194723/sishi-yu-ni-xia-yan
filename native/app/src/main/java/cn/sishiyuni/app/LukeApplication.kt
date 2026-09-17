package cn.sishiyuni.app

import android.app.Application
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.GraphOwner

/** One graph for activities, alarm receivers and WorkManager. Never opens the legacy app's files. */
class LukeApplication : Application(), GraphOwner {
    override val graph: AppGraph by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppGraph(applicationContext)
    }
}
