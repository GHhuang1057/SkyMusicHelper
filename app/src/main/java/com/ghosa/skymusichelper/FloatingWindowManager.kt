package com.ghosa.skymusichelper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView

class FloatingWindowManager(private val context: Context) {
    private var isWindowShowing = false
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView

    @SuppressLint("InflateParams")
    fun showFloatingWindow(
        onClose: () -> Unit,
        onToggleRunning: () -> Unit,
        isRunning: Boolean
    ) {
        if (isWindowShowing) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        composeView = ComposeView(context).apply {
            setContent {
                FloatingControlWindowContent(
                    onClose = onClose,
                    onToggleRunning = onToggleRunning,
                    isRunning = isRunning
                )
            }
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            x = 0
            y = 100
        }

        try {
            windowManager.addView(composeView, layoutParams)
            isWindowShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideFloatingWindow() {
        if (isWindowShowing && ::windowManager.isInitialized && ::composeView.isInitialized) {
            try {
                windowManager.removeView(composeView)
                isWindowShowing = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingControlWindowContent(
    onClose: () -> Unit,
    onToggleRunning: () -> Unit,
    isRunning: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "悬浮控制窗",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "停止点击" else "开始点击")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onClose) {
                Text("关闭")
            }
        }
    }
}