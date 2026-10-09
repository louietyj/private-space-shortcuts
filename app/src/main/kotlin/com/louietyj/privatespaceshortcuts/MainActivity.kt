package com.louietyj.privatespaceshortcuts

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAG = "MainActivity"

private data class PrivateApp(
    val component: ComponentName,
    val label: String,
    val icon: Drawable,
    val bitmap: ImageBitmap,
)

private data class PrivateShortcut(val shortcut: AppShortcut, val bitmap: ImageBitmap?)

private sealed interface SpaceState {
    data object NeedsSetup : SpaceState
    data object NotFound : SpaceState
    data class Locked(val user: UserHandle) : SpaceState
    data class Unlocked(val user: UserHandle) : SpaceState
}

class MainActivity : ComponentActivity() {

    private var state by mutableStateOf<SpaceState>(SpaceState.NeedsSetup)
    private var shizukuReady by mutableStateOf(false)

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) grantViaShizuku()
    }
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        shizukuReady = Permissions.isShizukuReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)

        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
                    Screen()
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        super.onDestroy()
    }

    private fun refresh() {
        shizukuReady = Permissions.isShizukuReady()
        state = when {
            Permissions.missing(this).isNotEmpty() -> SpaceState.NeedsSetup
            else -> when (val user = PrivateSpace.profile(this)) {
                null -> SpaceState.NotFound
                else -> if (PrivateSpace.isReady(this, user)) SpaceState.Unlocked(user) else SpaceState.Locked(user)
            }
        }
    }

    private fun grantViaShizuku() {
        if (!Permissions.grantViaShizuku(this)) {
            Toast.makeText(this, "Shizuku couldn't grant the permissions", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun onGrantClick() {
        when {
            Permissions.isShizukuReady() -> grantViaShizuku()
            Permissions.isShizukuRunning() -> Permissions.requestShizukuPermission()
            else -> Toast.makeText(this, "Shizuku isn't running", Toast.LENGTH_SHORT).show()
        }
    }

    /** The app's own shortcuts, or empty if Shizuku isn't available to read them. */
    private suspend fun loadShortcuts(user: UserHandle, app: PrivateApp): List<PrivateShortcut> {
        if (!Permissions.isShizukuReady()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                AppShortcuts.list(this@MainActivity, user, app.component.packageName)
                    .map { PrivateShortcut(it, it.icon?.toBitmap()?.asImageBitmap()) }
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't list shortcuts for ${app.component.packageName}", e)
                emptyList()
            }
        }
    }

    private fun pinApp(app: PrivateApp) = reportPinResult(Shortcuts.requestPin(this, app.component, app.label, app.icon))

    private fun pinShortcut(user: UserHandle, app: PrivateApp, shortcut: AppShortcut) {
        try {
            AppShortcuts.keepAlive(user, shortcut.packageName, shortcut.id)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep ${shortcut.packageName}/${shortcut.id} pinned", e)
        }
        reportPinResult(Shortcuts.requestPin(this, shortcut, app.icon))
    }

    private fun reportPinResult(supported: Boolean) {
        if (!supported) Toast.makeText(this, "Launcher doesn't support pinned shortcuts", Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun Screen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text("Private Shortcuts", fontSize = 24.sp, modifier = Modifier.padding(vertical = 16.dp))
            when (val s = state) {
                SpaceState.NeedsSetup -> SetupSection()
                SpaceState.NotFound -> Text("No private space found on this device.")
                is SpaceState.Locked -> LockedSection(s.user)
                is SpaceState.Unlocked -> AppList(s.user)
            }
        }
    }

    @Composable
    private fun SetupSection() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This app needs two permissions to open apps in private space. Grant them once with Shizuku:")
            Button(onClick = ::onGrantClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (shizukuReady) "Grant permissions" else "Grant permissions with Shizuku")
            }
            Text("…or over adb:")
            SelectionContainer {
                Text(
                    Permissions.adbCommands(this@MainActivity).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }

    @Composable
    private fun LockedSection(user: UserHandle) {
        var unlockRequested by remember { mutableStateOf(false) }
        // Unlocking finishes asynchronously after the credential prompt closes.
        LaunchedEffect(unlockRequested) {
            if (!unlockRequested) return@LaunchedEffect
            repeat(40) {
                delay(250)
                if (PrivateSpace.isReady(this@MainActivity, user)) return@LaunchedEffect refresh()
            }
            unlockRequested = false
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Unlock private space to choose apps. Existing shortcuts keep working while it's locked; they'll ask you to unlock.")
            Button(
                onClick = {
                    if (PrivateSpace.requestUnlock(this@MainActivity, user)) refresh() else unlockRequested = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Unlock private space") }
        }
    }

    @Composable
    private fun AppList(user: UserHandle) {
        var apps by remember(user) { mutableStateOf<List<PrivateApp>?>(null) }
        var picking by remember { mutableStateOf<Pair<PrivateApp, List<PrivateShortcut>>?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(user) {
            apps = withContext(Dispatchers.Default) { loadApps(user) }
        }
        Text(
            if (shizukuReady) {
                "Tap an app to add it or one of its shortcuts to your home screen, then drag it into your dock."
            } else {
                "Tap an app to add a shortcut to your home screen, then drag it into your dock. Start Shizuku to also pin an app's own shortcuts, like a chat."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val list = apps ?: return Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        LazyColumn {
            items(list, key = { it.component.flattenToShortString() }) { app ->
                ItemRow(app.bitmap, app.label) {
                    scope.launch {
                        val shortcuts = loadShortcuts(user, app)
                        if (shortcuts.isEmpty()) pinApp(app) else picking = app to shortcuts
                    }
                }
            }
        }

        val (app, shortcuts) = picking ?: return
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(app.label) },
            text = {
                LazyColumn {
                    item {
                        ItemRow(app.bitmap, "App") { picking = null; pinApp(app) }
                    }
                    items(shortcuts, key = { it.shortcut.id }) { (shortcut, bitmap) ->
                        ItemRow(bitmap ?: app.bitmap, shortcut.longLabel) {
                            picking = null
                            pinShortcut(user, app, shortcut)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun ItemRow(bitmap: ImageBitmap, label: String, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        ) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 16.sp)
        }
    }

    private fun loadApps(user: UserHandle): List<PrivateApp> =
        PrivateSpace.launcherActivities(this, user).map {
            val icon = PrivateSpace.loadIcon(this, user, it)
            PrivateApp(
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                label = PrivateSpace.loadLabel(this, user, it),
                icon = icon,
                bitmap = icon.toBitmap().asImageBitmap(),
            )
        }.sortedBy { it.label.lowercase() }
}
