package com.ghosa.skymusichelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import android.content.pm.PackageManager
import android.view.InputEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.platform.ComposeView



data class ClickPoint(val x: Int, val y: Int, val interval: Long)

class MainActivity : ComponentActivity() {
    private lateinit var configDatabaseHelper: ConfigDatabaseHelper
    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 处理权限请求结果，这里可添加权限获取成功或失败后的逻辑
    }

    private val requestFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val configJson = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    configJson.append(line)
                }
                reader.close()
                inputStream?.close()

                parseConfig(configJson.toString())
            } catch (e: Exception) {
                Toast.makeText(this, "配置文件解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            requestFileLauncher.launch("application/json")
        } else {
            Toast.makeText(this, "文件读取权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configDatabaseHelper = ConfigDatabaseHelper(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoClickerApp(
                        configDatabaseHelper = configDatabaseHelper,
                        onImportConfig = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.READ_MEDIA_IMAGES
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestFileLauncher.launch("application/json")
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                }
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestFileLauncher.launch("application/json")
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun parseConfig(configJson: String) {
        try {
            val jsonObject = JSONObject(configJson)
            val clickPointsJson = jsonObject.getJSONArray("click_points")
            val points = mutableListOf<ClickPoint>()
            for (i in 0 until clickPointsJson.length()) {
                val pointJson = clickPointsJson.getJSONObject(i)
                val x = pointJson.getInt("x")
                val y = pointJson.getInt("y")
                val interval = pointJson.getLong("interval")
                points.add(ClickPoint(x, y, interval))
            }

            // 更新数据库和界面状态
            saveClickPoints(configDatabaseHelper, points)
            Toast.makeText(this, "配置文件导入成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "配置文件解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        requestOverlayPermissionLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickerApp(
    configDatabaseHelper: ConfigDatabaseHelper,
    onImportConfig: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFloatingWindow by remember { mutableStateOf(false) }
    var shizukuAvailable by remember { mutableStateOf(false) }
    // 从数据库读取点击点列表
    var clickPoints by remember {
        mutableStateOf(loadClickPoints(configDatabaseHelper))
    }
    var isRunning by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val runningFlag = remember { AtomicBoolean(false) }
    val floatingWindowManager = remember { FloatingWindowManager(context) }

    // Shizuku 监听器
    DisposableEffect(Unit) {
        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                shizukuAvailable = Shizuku.isPreV11() || Shizuku.getBinder() != null
            }
        }
        Shizuku.addBinderReceivedListenerSticky(listener)
        onDispose {
            Shizuku.removeBinderReceivedListener(listener)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("SkyMusicHelper", fontWeight = FontWeight.Bold) },
                actions = {
                    // 新增悬浮窗设置按钮
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("请先授予悬浮窗权限")
                                // 再次请求权限
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        } else {
                            val intent = Intent(context, FloatingWindowSettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "悬浮窗设置"
                        )
                    }
                    // 替换弃用的图标
                    IconButton(onClick = onImportConfig) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "导入配置文件"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, "添加点击点")
                }
                Spacer(modifier = Modifier.height(16.dp))
                FloatingActionButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("请先授予悬浮窗权限")
                                // 再次请求权限
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        } else {
                            if (showFloatingWindow) {
                                floatingWindowManager.hideFloatingWindow()
                            } else {
                                floatingWindowManager.showFloatingWindow(
                                    onClose = {
                                        showFloatingWindow = false
                                        floatingWindowManager.hideFloatingWindow()
                                    },
                                    onToggleRunning = {
                                        if (!shizukuAvailable) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("请先连接Shizuku服务")
                                            }
                                            return@showFloatingWindow
                                        }
                                        if (clickPoints.isEmpty()) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("请先添加点击点位")
                                            }
                                            return@showFloatingWindow
                                        }
                                        isRunning = !isRunning
                                        runningFlag.set(isRunning)
                                    },
                                    isRunning = isRunning
                                )
                            }
                            showFloatingWindow = !showFloatingWindow
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ) {
                    Icon(
                        if (showFloatingWindow) Icons.Default.Close else Icons.Default.Add,
                        if (showFloatingWindow) "关闭悬浮窗" else "打开悬浮窗"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shizuku 状态指示
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (shizukuAvailable) Color.Green else Color.Red
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (shizukuAvailable) "Shizuku 已连接" else "Shizuku 未连接",
                        color = if (shizukuAvailable) Color.Green else Color.Red
                    )
                }

                // 控制面板
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "控制面板",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    if (!shizukuAvailable) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("请先连接Shizuku服务")
                                        }
                                        return@Button
                                    }
                                    if (clickPoints.isEmpty()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("请先添加点击点位")
                                        }
                                        return@Button
                                    }
                                    isRunning = !isRunning
                                    runningFlag.set(isRunning)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "开始/停止",
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(if (isRunning) "停止点击" else "开始点击")
                            }

                            Button(onClick = { clickPoints = emptyList() }) {
                                Text("清空点位")
                            }
                        }
                    }
                }

                // 点击点列表
                Text(
                    text = "点击点位列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .align(Alignment.Start)
                )

                if (clickPoints.isEmpty()) {
                    Text(
                        text = "暂无点击点，点击右下角 + 添加",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    clickPoints.forEachIndexed { index, point ->
                        PointItem(
                            point = point,
                            index = index,
                            onRemove = { clickPoints = clickPoints.toMutableList().apply { removeAt(index) } }
                        )
                    }
                }
            }
        }
    }

    // 添加点位的对话框
    if (showAddDialog) {
        AddPointDialog(
            onDismiss = { showAddDialog = false },
            onAddPoint = { x, y, interval ->
                clickPoints = clickPoints + ClickPoint(x, y, interval)
                saveClickPoints(configDatabaseHelper, clickPoints)
            },
            onError = { errorMsg ->
                errorMessage = errorMsg
                showErrorDialog = true
            }
        )
    }

    // 悬浮窗控制
    if (showFloatingWindow) {
        FloatingControlWindow(
            onClose = { showFloatingWindow = false },
            onToggleRunning = {
                if (!shizukuAvailable) {
                    scope.launch {
                        snackbarHostState.showSnackbar("请先连接Shizuku服务")
                    }
                    return@FloatingControlWindow
                }
                if (clickPoints.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("请先添加点击点位")
                    }
                    return@FloatingControlWindow
                }
                isRunning = !isRunning
                runningFlag.set(isRunning)
            },
            isRunning = isRunning
        )
    }

    // 错误对话框
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("错误") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    // 自动点击逻辑
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val executor = Executors.newSingleThreadScheduledExecutor()
            var currentIndex = 0
            val future = executor.scheduleAtFixedRate({
                if (runningFlag.get() && shizukuAvailable && clickPoints.isNotEmpty()) {
                    val point = clickPoints[currentIndex]
                    try {
                        performClick(point.x, point.y)
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("点击操作失败: ${e.message}")
                        }
                    }
                    currentIndex = (currentIndex + 1) % clickPoints.size
                }
            }, 0, clickPoints.getOrNull(0)?.interval ?: 500, TimeUnit.MILLISECONDS)

            try {
                while (isRunning) {
                    delay(100)
                }
            } finally {
                future.cancel(true)
                executor.shutdown()
            }
        }
    }
}

private fun loadClickPoints(configDatabaseHelper: ConfigDatabaseHelper): List<ClickPoint> {
    val clickPointsJson = configDatabaseHelper.getConfigValue("click_points") ?: "[]"
    return try {
        val jsonArray = JSONArray(clickPointsJson)
        val points = mutableListOf<ClickPoint>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val x = jsonObject.getInt("x")
            val y = jsonObject.getInt("y")
            val interval = jsonObject.getLong("interval")
            points.add(ClickPoint(x, y, interval))
        }
        points
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveClickPoints(configDatabaseHelper: ConfigDatabaseHelper, clickPoints: List<ClickPoint>) {
    val jsonArray = JSONArray()
    clickPoints.forEach { point ->
        val jsonObject = JSONObject()
        jsonObject.put("x", point.x)
        jsonObject.put("y", point.y)
        jsonObject.put("interval", point.interval)
        jsonArray.put(jsonObject)
    }
    configDatabaseHelper.setConfigValue("click_points", jsonArray.toString())
}

@Composable
fun PointItem(point: ClickPoint, index: Int, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "点 ${index + 1}",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "X: ${point.x}, Y: ${point.y}, 间隔: ${point.interval}ms",
                modifier = Modifier.weight(2f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "删除")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPointDialog(
    onDismiss: () -> Unit,
    onAddPoint: (x: Int, y: Int, interval: Long) -> Unit,
    onError: (String) -> Unit
) {
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "添加点击点",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it },
                    label = { Text("X 坐标") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y 坐标") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("点击间隔 (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val xVal = x.toIntOrNull()
                        val yVal = y.toIntOrNull()
                        val intervalVal = interval.toLongOrNull()
                        if (xVal != null && yVal != null && intervalVal != null && intervalVal > 0) {
                            onAddPoint(xVal, yVal, intervalVal)
                            onDismiss()
                        } else {
                            onError("请输入有效的 X、Y 坐标和正的点击间隔")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = x.isNotBlank() && y.isNotBlank() && interval.isNotBlank()
                ) {
                    Text("添加点位")
                }
            }
        }
    }
}

@Composable
fun FloatingControlWindow(
    onClose: () -> Unit,
    onToggleRunning: () -> Unit,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            x = 0
            y = 100
        }

        val view = ComposeView(context).apply {
            setContent {

            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                windowManager.addView(view, layoutParams)
            } else {
                Toast.makeText(context, "没有悬浮窗权限，无法显示悬浮窗", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "悬浮窗添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        onDispose {
            coroutineScope.launch {
                delay(100)
                try {
                    if (view.parent != null) {
                        windowManager.removeView(view)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}



@SuppressLint("PrivateApi", "BlockedPrivateApi")
fun performClick(x: Int, y: Int) {
    try {
        val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
        val inputManager = SystemServiceHelper.getSystemService("input")

        val method = iInputManagerClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        )

        val eventTime = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(
            eventTime, eventTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0
        )
        val upEvent = MotionEvent.obtain(
            eventTime, eventTime, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0
        )

        method.invoke(
            iInputManagerClass.cast(ShizukuBinderWrapper(inputManager as IBinder)),
            downEvent, 0
        )
        method.invoke(
            iInputManagerClass.cast(ShizukuBinderWrapper(inputManager as IBinder)),
            upEvent, 0
        )

        downEvent.recycle()
        upEvent.recycle()
    } catch (e: Exception) {
        throw RuntimeException("模拟点击失败", e)
    }
}


    fun hideFloatingWindow() {
        // 隐藏悬浮窗逻辑
    }
