package cn.sishiyuni.uitesthost

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.GraphOwner

/** Deliberately no product UI or launcher intent: tests supply the screen being checked. */
class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge() }
}
class TestApplication : Application(), GraphOwner {
    override val graph: AppGraph by lazy { AppGraph(this) }
}
