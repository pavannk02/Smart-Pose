package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Color definitions for glowing neon wireframes
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF39FF14)
val NeonPink = Color(0xFFFF2A85)
val NeonAmber = Color(0xFFFFB300)

@Composable
fun PoseWireframe(
    styleName: String,
    modifier: Modifier = Modifier,
    glowColor: Color = NeonCyan
) {
    // Elegant breathing pulse effect for the glow
    val infiniteTransition = rememberInfiniteTransition(label = "wireframe_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    val scaleAnimate by transitionToScale()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            // 1. Draw Rule-of-Thirds Grid in very thin, semi-transparent white dashed lines
            val gridPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
            val gridColor = Color.White.copy(alpha = 0.25f)
            
            // Vertical lines
            drawLine(
                color = gridColor,
                start = Offset(width / 3f, 0f),
                end = Offset(width / 3f, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridPathEffect
            )
            drawLine(
                color = gridColor,
                start = Offset(2f * width / 3f, 0f),
                end = Offset(2f * width / 3f, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridPathEffect
            )
            // Horizontal lines
            drawLine(
                color = gridColor,
                start = Offset(0f, height / 3f),
                end = Offset(width, height / 3f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridPathEffect
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, 2f * height / 3f),
                end = Offset(width, 2f * height / 3f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridPathEffect
            )

            // Draw center helper target circle in middle third intersection
            drawCircle(
                color = glowColor.copy(alpha = 0.15f),
                radius = 12.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.dp.toPx(), pathEffect = gridPathEffect)
            )

            // 2. Select wireframe model based on returned style name
            val strokeWidthNormal = 3.dp.toPx()
            val strokeWidthBold = 5.dp.toPx()
            val finalColor = glowColor.copy(alpha = pulseAlpha)

            when (styleName.trim().lowercase()) {
                "seated", "sartorial seated", "seated coffee cup hold" -> {
                    // Draw a gorgeous sitting posture guide
                    val headCenter = Offset(centerX, centerY - 140.dp.toPx() * scaleAnimate)
                    val shoulderLeft = Offset(centerX - 40.dp.toPx() * scaleAnimate, centerY - 80.dp.toPx() * scaleAnimate)
                    val shoulderRight = Offset(centerX + 40.dp.toPx() * scaleAnimate, centerY - 80.dp.toPx() * scaleAnimate)
                    val hipLeft = Offset(centerX - 35.dp.toPx() * scaleAnimate, centerY + 60.dp.toPx() * scaleAnimate)
                    val hipRight = Offset(centerX + 25.dp.toPx() * scaleAnimate, centerY + 60.dp.toPx() * scaleAnimate)
                    
                    // Chair seat support and knee extension
                    val kneeLeft = Offset(centerX - 80.dp.toPx() * scaleAnimate, centerY + 80.dp.toPx() * scaleAnimate)
                    val kneeRight = Offset(centerX + 20.dp.toPx() * scaleAnimate, centerY + 130.dp.toPx() * scaleAnimate)
                    
                    // Feet
                    val footLeft = Offset(centerX - 70.dp.toPx() * scaleAnimate, centerY + 220.dp.toPx() * scaleAnimate)
                    val footRight = Offset(centerX + 30.dp.toPx() * scaleAnimate, centerY + 240.dp.toPx() * scaleAnimate)

                    // Elbows & Hands holding a coffee cup
                    val elbowLeft = Offset(centerX - 65.dp.toPx() * scaleAnimate, centerY - 10.dp.toPx() * scaleAnimate)
                    val elbowRight = Offset(centerX + 65.dp.toPx() * scaleAnimate, centerY - 15.dp.toPx() * scaleAnimate)
                    val handLeft = Offset(centerX - 15.dp.toPx() * scaleAnimate, centerY - 5.dp.toPx() * scaleAnimate)
                    val handRight = Offset(centerX + 15.dp.toPx() * scaleAnimate, centerY - 5.dp.toPx() * scaleAnimate)

                    // Draw body skeleton connections
                    drawCircle(finalColor, radius = 24.dp.toPx() * scaleAnimate, center = headCenter, style = Stroke(strokeWidthNormal))
                    
                    // Spine / Torso outline
                    drawLine(finalColor, (shoulderLeft + shoulderRight)/2f, (hipLeft + hipRight)/2f, strokeWidthNormal)
                    drawLine(finalColor, shoulderLeft, shoulderRight, strokeWidthNormal)
                    drawLine(finalColor, hipLeft, hipRight, strokeWidthNormal)

                    // Left Arm holding cup
                    drawLine(finalColor, shoulderLeft, elbowLeft, strokeWidthNormal)
                    drawLine(finalColor, elbowLeft, handLeft, strokeWidthNormal)

                    // Right Arm holding cup
                    drawLine(finalColor, shoulderRight, elbowRight, strokeWidthNormal)
                    drawLine(finalColor, elbowRight, handRight, strokeWidthNormal)

                    // Left Leg (seated)
                    drawLine(finalColor, hipLeft, kneeLeft, strokeWidthNormal)
                    drawLine(finalColor, kneeLeft, footLeft, strokeWidthNormal)

                    // Right Leg (seated cross-legged)
                    drawLine(finalColor, hipRight, kneeRight, strokeWidthNormal)
                    drawLine(finalColor, kneeRight, footRight, strokeWidthNormal)

                    // Draw Joint nodes to look high-tech
                    val joints = listOf(shoulderLeft, shoulderRight, elbowLeft, elbowRight, handLeft, handRight, hipLeft, hipRight, kneeLeft, kneeRight, footLeft, footRight)
                    joints.forEach { joint ->
                        drawCircle(finalColor, radius = 5.dp.toPx(), center = joint)
                        drawCircle(glowColor.copy(alpha = 0.2f), radius = 10.dp.toPx(), center = joint)
                    }

                    // Coffee cup cute outline path
                    val cupPath = Path().apply {
                        val cupLeft = centerX - 10.dp.toPx() * scaleAnimate
                        val cupRight = centerX + 10.dp.toPx() * scaleAnimate
                        val cupTop = centerY - 15.dp.toPx() * scaleAnimate
                        val cupBottom = centerY + 5.dp.toPx() * scaleAnimate
                        
                        moveTo(cupLeft, cupTop)
                        lineTo(cupRight, cupTop)
                        lineTo(cupRight - 3.dp.toPx(), cupBottom)
                        lineTo(cupLeft + 3.dp.toPx(), cupBottom)
                        close()
                        
                        // Handle
                        moveTo(cupRight - 1.dp.toPx(), cupTop + 4.dp.toPx())
                        quadraticTo(cupRight + 8.dp.toPx(), cupTop + 8.dp.toPx(), cupRight - 1.dp.toPx(), cupTop + 12.dp.toPx())
                    }
                    drawPath(cupPath, finalColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }

                "candid walking", "walking", "street walk" -> {
                    // Draw a walk-in-progress guide
                    val headCenter = Offset(centerX - 10.dp.toPx(), centerY - 160.dp.toPx() * scaleAnimate)
                    val shoulderLeft = Offset(centerX - 42.dp.toPx() * scaleAnimate, centerY - 95.dp.toPx() * scaleAnimate)
                    val shoulderRight = Offset(centerX + 32.dp.toPx() * scaleAnimate, centerY - 92.dp.toPx() * scaleAnimate)
                    val hipLeft = Offset(centerX - 30.dp.toPx() * scaleAnimate, centerY + 40.dp.toPx() * scaleAnimate)
                    val hipRight = Offset(centerX + 26.dp.toPx() * scaleAnimate, centerY + 40.dp.toPx() * scaleAnimate)

                    // Arms dynamically swings
                    val elbowLeft = Offset(centerX - 68.dp.toPx() * scaleAnimate, centerY - 25.dp.toPx() * scaleAnimate)
                    val elbowRight = Offset(centerX + 58.dp.toPx() * scaleAnimate, centerY - 40.dp.toPx() * scaleAnimate)
                    val handLeft = Offset(centerX - 45.dp.toPx() * scaleAnimate, centerY + 30.dp.toPx() * scaleAnimate)
                    val handRight = Offset(centerX + 75.dp.toPx() * scaleAnimate, centerY + 18.dp.toPx() * scaleAnimate)

                    // Walk stride legs
                    val kneeLeft = Offset(centerX - 65.dp.toPx() * scaleAnimate, centerY + 120.dp.toPx() * scaleAnimate)
                    val kneeRight = Offset(centerX + 55.dp.toPx() * scaleAnimate, centerY + 110.dp.toPx() * scaleAnimate)
                    val footLeft = Offset(centerX - 95.dp.toPx() * scaleAnimate, centerY + 240.dp.toPx() * scaleAnimate)
                    val footRight = Offset(centerX + 85.dp.toPx() * scaleAnimate, centerY + 235.dp.toPx() * scaleAnimate)

                    drawCircle(finalColor, radius = 25.dp.toPx() * scaleAnimate, center = headCenter, style = Stroke(strokeWidthNormal))
                    drawLine(finalColor, (shoulderLeft + shoulderRight)/2f, (hipLeft + hipRight)/2f, strokeWidthNormal)
                    drawLine(finalColor, shoulderLeft, shoulderRight, strokeWidthNormal)
                    drawLine(finalColor, hipLeft, hipRight, strokeWidthNormal)

                    // Arms
                    drawLine(finalColor, shoulderLeft, elbowLeft, strokeWidthNormal)
                    drawLine(finalColor, elbowLeft, handLeft, strokeWidthNormal)
                    drawLine(finalColor, shoulderRight, elbowRight, strokeWidthNormal)
                    drawLine(finalColor, elbowRight, handRight, strokeWidthNormal)

                    // Walking legs stride
                    drawLine(finalColor, hipLeft, kneeLeft, strokeWidthNormal)
                    drawLine(finalColor, kneeLeft, footLeft, strokeWidthNormal)
                    drawLine(finalColor, hipRight, kneeRight, strokeWidthNormal)
                    drawLine(finalColor, kneeRight, footRight, strokeWidthNormal)

                    // Joints
                    val joints = listOf(shoulderLeft, shoulderRight, elbowLeft, elbowRight, handLeft, handRight, hipLeft, hipRight, kneeLeft, kneeRight, footLeft, footRight)
                    joints.forEach { joint ->
                        drawCircle(finalColor, radius = 4.5f.dp.toPx(), center = joint)
                        drawCircle(glowColor.copy(alpha = 0.15f), radius = 9.dp.toPx(), center = joint)
                    }
                }

                "over-the-shoulder", "shoulder lean", "candid lookback" -> {
                    // Turnback lookback portrait
                    val headCenter = Offset(centerX - 25.dp.toPx() * scaleAnimate, centerY - 145.dp.toPx() * scaleAnimate)
                    val shoulderLeft = Offset(centerX - 60.dp.toPx() * scaleAnimate, centerY - 65.dp.toPx() * scaleAnimate)
                    val shoulderRight = Offset(centerX + 25.dp.toPx() * scaleAnimate, centerY - 80.dp.toPx() * scaleAnimate)
                    
                    // We see more back
                    val hipLeft = Offset(centerX - 40.dp.toPx() * scaleAnimate, centerY + 85.dp.toPx() * scaleAnimate)
                    val hipRight = Offset(centerX + 30.dp.toPx() * scaleAnimate, centerY + 75.dp.toPx() * scaleAnimate)

                    // Arm tucked
                    val elbowLeft = Offset(centerX - 80.dp.toPx() * scaleAnimate, centerY + 15.dp.toPx() * scaleAnimate)
                    val handLeft = Offset(centerX - 15.dp.toPx() * scaleAnimate, centerY + 45.dp.toPx() * scaleAnimate)

                    drawCircle(finalColor, radius = 26.dp.toPx() * scaleAnimate, center = headCenter, style = Stroke(strokeWidthNormal))
                    drawLine(finalColor, shoulderLeft, shoulderRight, strokeWidthNormal)
                    drawLine(finalColor, (shoulderLeft + shoulderRight)/2f, (hipLeft + hipRight)/2f, strokeWidthNormal)
                    drawLine(finalColor, hipLeft, hipRight, strokeWidthNormal)

                    drawLine(finalColor, shoulderLeft, elbowLeft, strokeWidthNormal)
                    drawLine(finalColor, elbowLeft, handLeft, strokeWidthNormal)

                    // Elegant lines down
                    drawLine(finalColor, hipLeft, Offset(hipLeft.x - 15.dp.toPx(), height), strokeWidthNormal)
                    drawLine(finalColor, hipRight, Offset(hipRight.x + 15.dp.toPx(), height), strokeWidthNormal)

                    // Look direction indicator arrow
                    val gazeStart = Offset(headCenter.x + 10.dp.toPx(), headCenter.y)
                    val gazeEnd = Offset(headCenter.x + 55.dp.toPx(), headCenter.y - 12.dp.toPx())
                    drawLine(glowColor, gazeStart, gazeEnd, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f,5f)))
                    
                    val joints = listOf(shoulderLeft, shoulderRight, elbowLeft, handLeft, hipLeft, hipRight)
                    joints.forEach { joint ->
                        drawCircle(finalColor, radius = 5.dp.toPx(), center = joint)
                    }
                }

                "crossed arms", "arms folded", "confident stance" -> {
                    val headCenter = Offset(centerX, centerY - 155.dp.toPx() * scaleAnimate)
                    val shoulderLeft = Offset(centerX - 55.dp.toPx() * scaleAnimate, centerY - 90.dp.toPx() * scaleAnimate)
                    val shoulderRight = Offset(centerX + 55.dp.toPx() * scaleAnimate, centerY - 90.dp.toPx() * scaleAnimate)
                    
                    val elbowLeft = Offset(centerX - 75.dp.toPx() * scaleAnimate, centerY - 15.dp.toPx() * scaleAnimate)
                    val elbowRight = Offset(centerX + 75.dp.toPx() * scaleAnimate, centerY - 15.dp.toPx() * scaleAnimate)

                    // Arms overlapping across breast
                    val armJoinLeft = Offset(centerX - 25.dp.toPx() * scaleAnimate, centerY - 25.dp.toPx() * scaleAnimate)
                    val armJoinRight = Offset(centerX + 25.dp.toPx() * scaleAnimate, centerY - 25.dp.toPx() * scaleAnimate)

                    val hipLeft = Offset(centerX - 38.dp.toPx() * scaleAnimate, centerY + 80.dp.toPx() * scaleAnimate)
                    val hipRight = Offset(centerX + 38.dp.toPx() * scaleAnimate, centerY + 80.dp.toPx() * scaleAnimate)

                    val kneeLeft = Offset(centerX - 42.dp.toPx() * scaleAnimate, centerY + 180.dp.toPx() * scaleAnimate)
                    val kneeRight = Offset(centerX + 42.dp.toPx() * scaleAnimate, centerY + 180.dp.toPx() * scaleAnimate)

                    drawCircle(finalColor, radius = 25.dp.toPx() * scaleAnimate, center = headCenter, style = Stroke(strokeWidthNormal))
                    drawLine(finalColor, shoulderLeft, shoulderRight, strokeWidthNormal)
                    drawLine(finalColor, (shoulderLeft + shoulderRight)/2f, (hipLeft + hipRight)/2f, strokeWidthNormal)
                    drawLine(finalColor, hipLeft, hipRight, strokeWidthNormal)

                    // Arms crossed path
                    drawLine(finalColor, shoulderLeft, elbowLeft, strokeWidthNormal)
                    drawLine(finalColor, elbowLeft, armJoinRight, strokeWidthBold) // Bold because folded
                    drawLine(finalColor, shoulderRight, elbowRight, strokeWidthNormal)
                    drawLine(finalColor, elbowRight, armJoinLeft, strokeWidthBold)

                    // Legs
                    drawLine(finalColor, hipLeft, kneeLeft, strokeWidthNormal)
                    drawLine(finalColor, hipRight, kneeRight, strokeWidthNormal)
                    drawLine(finalColor, kneeLeft, Offset(kneeLeft.x, height), strokeWidthNormal)
                    drawLine(finalColor, kneeRight, Offset(kneeRight.x, height), strokeWidthNormal)

                    val joints = listOf(shoulderLeft, shoulderRight, elbowLeft, elbowRight, hipLeft, hipRight, kneeLeft, kneeRight)
                    joints.forEach { joint ->
                        drawCircle(finalColor, radius = 5.dp.toPx(), center = joint)
                    }
                }

                else -> { // "Classic Standing" / defaulted state
                    // Golden Ratio standing pose
                    val headCenter = Offset(centerX, centerY - 160.dp.toPx() * scaleAnimate)
                    val shoulderLeft = Offset(centerX - 50.dp.toPx() * scaleAnimate, centerY - 95.dp.toPx() * scaleAnimate)
                    val shoulderRight = Offset(centerX + 50.dp.toPx() * scaleAnimate, centerY - 95.dp.toPx() * scaleAnimate)
                    
                    val elbowLeft = Offset(centerX - 70.dp.toPx() * scaleAnimate, centerY - 15.dp.toPx() * scaleAnimate)
                    val elbowRight = Offset(centerX + 70.dp.toPx() * scaleAnimate, centerY - 25.dp.toPx() * scaleAnimate)
                    
                    // Left hand is casually on the hip hip pocket, right arm drops freely down
                    val handLeft = Offset(centerX - 42.dp.toPx() * scaleAnimate, centerY + 45.dp.toPx() * scaleAnimate)
                    val handRight = Offset(centerX + 68.dp.toPx() * scaleAnimate, centerY + 40.dp.toPx() * scaleAnimate)

                    val hipLeft = Offset(centerX - 35.dp.toPx() * scaleAnimate, centerY + 65.dp.toPx() * scaleAnimate)
                    val hipRight = Offset(centerX + 35.dp.toPx() * scaleAnimate, centerY + 65.dp.toPx() * scaleAnimate)

                    val kneeLeft = Offset(centerX - 38.dp.toPx() * scaleAnimate, centerY + 175.dp.toPx() * scaleAnimate)
                    val kneeRight = Offset(centerX + 38.dp.toPx() * scaleAnimate, centerY + 170.dp.toPx() * scaleAnimate)

                    val footLeft = Offset(centerX - 40.dp.toPx() * scaleAnimate, centerY + 270.dp.toPx() * scaleAnimate)
                    val footRight = Offset(centerX + 45.dp.toPx() * scaleAnimate, centerY + 265.dp.toPx() * scaleAnimate)

                    // Draw connections
                    drawCircle(finalColor, radius = 25.dp.toPx() * scaleAnimate, center = headCenter, style = Stroke(strokeWidthNormal))
                    drawLine(finalColor, shoulderLeft, shoulderRight, strokeWidthNormal)
                    drawLine(finalColor, (shoulderLeft + shoulderRight)/2f, (hipLeft + hipRight)/2f, strokeWidthNormal)
                    drawLine(finalColor, hipLeft, hipRight, strokeWidthNormal)

                    // Left Arm (bent, hand resting on hips)
                    drawLine(finalColor, shoulderLeft, elbowLeft, strokeWidthNormal)
                    drawLine(finalColor, elbowLeft, handLeft, strokeWidthNormal)

                    // Right Arm (natural long line)
                    drawLine(finalColor, shoulderRight, elbowRight, strokeWidthNormal)
                    drawLine(finalColor, elbowRight, handRight, strokeWidthNormal)

                    // Left leg
                    drawLine(finalColor, hipLeft, kneeLeft, strokeWidthNormal)
                    drawLine(finalColor, kneeLeft, footLeft, strokeWidthNormal)

                    // Right leg
                    drawLine(finalColor, hipRight, kneeRight, strokeWidthNormal)
                    drawLine(finalColor, kneeRight, footRight, strokeWidthNormal)

                    // Joints
                    val joints = listOf(shoulderLeft, shoulderRight, elbowLeft, elbowRight, handLeft, handRight, hipLeft, hipRight, kneeLeft, kneeRight, footLeft, footRight)
                    joints.forEach { joint ->
                        drawCircle(finalColor, radius = 5.dp.toPx(), center = joint)
                        drawCircle(glowColor.copy(alpha = 0.2f), radius = 10.dp.toPx(), center = joint)
                    }
                }
            }
        }
    }
}

@Composable
private fun transitionToScale(): State<Float> {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    return scale.asState()
}
