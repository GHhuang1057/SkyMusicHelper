package com.ghosa.skymusichelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

class FloatingWindowSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 这里调用 @Composable 函数是正确的，因为 setContent 内部是 @Composable 上下文
                    FloatingWindowSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun FloatingWindowSettingsScreen() {
    var positionX by remember { mutableStateOf(0) }
    var positionY by remember { mutableStateOf(100) }
    // 修正：将 MaterialTheme.colorScheme.primary 提取到 @Composable 上下文中
    val primaryColor = MaterialTheme.colorScheme.primary
    var buttonColor by remember { mutableStateOf(primaryColor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "悬浮窗设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // X 坐标设置
        OutlinedTextField(
            value = positionX.toString(),
            onValueChange = {
                positionX = it.toIntOrNull() ?: positionX
            },
            label = { Text("X 坐标") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )

        // Y 坐标设置
        OutlinedTextField(
            value = positionY.toString(),
            onValueChange = {
                positionY = it.toIntOrNull() ?: positionY
            },
            label = { Text("Y 坐标") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )

        // 按钮颜色设置
        ColorPicker(
            selectedColor = buttonColor,
            onColorSelected = { buttonColor = it }
        )

        Button(onClick = {
            // 这里可以添加保存设置的逻辑
        }) {
            Text("保存设置")
        }
    }
}

@Composable
fun ColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        Color.Red,
        Color.Green,
        Color.Blue
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { color ->
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onColorSelected(color) },
                shape = RoundedCornerShape(8.dp),
                color = color
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (color == selectedColor) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}