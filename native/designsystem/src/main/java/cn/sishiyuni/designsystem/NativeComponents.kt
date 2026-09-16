package cn.sishiyuni.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.sishiyuni.core.AppGraph
import coil.compose.SubcomposeAsyncImage
import java.io.File

@Composable
inline fun <reified VM: ViewModel> nativeViewModel(graph: AppGraph, key: String, noinline create: () -> VM): VM {
    val factory = remember(graph, key) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    } }
    return viewModel(key = key, factory = factory)
}

@Composable
fun LukeCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSeason.current
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = colors.paper,
        contentColor = colors.ink, border = BorderStroke(.7.dp, colors.border.copy(alpha = .8f))) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun AssetImage(path: String, description: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    val model: Any = remember(path, context) {
        when {
            path.startsWith("images/") && !path.contains("..") -> "file:///android_asset/$path"
            path.startsWith(context.filesDir.absolutePath + "/") -> File(path)
            else -> "file:///android_asset/images/companions/cat.webp"
        }
    }
    SubcomposeAsyncImage(model, description, modifier, contentScale = contentScale,
        error = { Icon(Icons.Outlined.PersonOutline, description, Modifier.fillMaxSize().padding(4.dp), tint = LocalSeason.current.muted) })
}

@Composable
fun ErrorNotice(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f).padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭错误提示") }
        }
    }
}

@Composable
fun NativeDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 14.dp)
            .imePadding().heightIn(max = 680.dp), color = LocalSeason.current.paper,
            shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭$title") }
                }
                content()
            }
        }
    }
}

@Composable
fun NativeTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        Text(title, Modifier.weight(1f).padding(start = if (onBack == null) 6.dp else 0.dp), style = MaterialTheme.typography.titleLarge)
        actions()
    }
}
