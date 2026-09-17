package cn.sishiyuni.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.GraphOwner

/** Production launcher. The instrumentation host is not a dependency of this application. */
class MainActivity : ComponentActivity() {
    private var appGraph: AppGraph? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val opened = runCatching { (application as GraphOwner).graph }
        appGraph = opened.getOrNull()
        setContent {
            val graph = appGraph
            if (graph != null) LukeApp(graph, this)
            else StartupFailure(opened.exceptionOrNull()?.message ?: "无法打开本机数据")
        }
    }

    override fun onStop() {
        // App-owned writes are not cancelled with an activity or a pager page.
        appGraph?.drafts?.let { drafts ->
            drafts.state.value.keys.forEach(drafts::flushInBackground)
        }
        super.onStop()
    }
}
