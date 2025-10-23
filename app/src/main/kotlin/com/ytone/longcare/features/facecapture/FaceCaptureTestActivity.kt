package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

/**
 * 人脸捕获功能测试Activity
 * 用于验证人脸捕获功能的完整性
 */
@AndroidEntryPoint
class FaceCaptureTestActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FaceCaptureScreen(
                        onFaceSelected = { bitmap ->
                            handleFaceSelected(bitmap)
                        },
                        onNavigateBack = {
                            finish()
                        }
                    )
                }
            }
        }
    }
    
    /**
     * 处理选择的人脸图片
     * @param bitmap 选择的人脸图片
     */
    private fun handleFaceSelected(bitmap: Bitmap) {
        // 这里可以保存图片或进行其他处理
        Toast.makeText(
            this, 
            "人脸捕获成功！图片尺寸: ${bitmap.width}x${bitmap.height}", 
            Toast.LENGTH_LONG
        ).show()
        
        // 可以在这里添加保存图片的逻辑
        // saveBitmapToFile(bitmap)
        
        // 返回结果
        setResult(RESULT_OK)
        finish()
    }
}