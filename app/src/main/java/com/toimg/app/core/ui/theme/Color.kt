package com.toimg.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 极简建筑风格调色板：纯色、高对比度、主次分明、零模糊、零多余特效 ----

// 浅色模式基底
val Primary = Color(0xFF0F172A)              // 深邃石墨黑 (标题与核心主动作)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFF1F5F9)     // 极淡石板灰底 (主容器)
val OnPrimaryContainer = Color(0xFF0F172A)

val Accent = Color(0xFF2563EB)               // 国际克莱因蓝 (重点指示与操作微强调)
val OnAccent = Color(0xFFFFFFFF)
val AccentContainer = Color(0xFFEFF6FF)
val OnAccentContainer = Color(0xFF1E40AF)

val Secondary = Color(0xFF475569)            // 严谨中性灰 (次要标签、副标题)
val SecondaryContainer = Color(0xFFF8FAFC)
val OnSecondaryContainer = Color(0xFF334155)

val Tertiary = Color(0xFF0D9488)             // 稳健青松绿 (完成与成功状态)
val TertiaryContainer = Color(0xFFF0FDFA)
val OnTertiaryContainer = Color(0xFF115E59)

val ErrorRed = Color(0xFFDC2626)             // 警告与错误纯红
val ErrorContainerLight = Color(0xFFFEF2F2)
val OnErrorContainerLight = Color(0xFF991B1B)

val Canvas = Color(0xFFF8FAFC)               // 纯净画布背景
val SurfaceWhite = Color(0xFFFFFFFF)         // 实体卡片纯白
val SurfaceVariantLight = Color(0xFFF1F5F9)  // 辅助块纯色
val Ink = Color(0xFF0F172A)                  // 正文高对比度文字
val MutedInk = Color(0xFF64748B)             // 辅助说明中灰文字
val OutlineLight = Color(0xFFCBD5E1)         // 1dp 发丝分割细线
val DividerGray = Color(0xFFE2E8F0)          // 网格分割线

// ---- 深色模式基底 ----
val PrimaryDark = Color(0xFFF8FAFC)
val OnPrimaryDark = Color(0xFF0F172A)
val PrimaryContainerDark = Color(0xFF1E293B)
val OnPrimaryContainerDark = Color(0xFFF8FAFC)

val SecondaryDark = Color(0xFF94A3B8)
val SecondaryContainerDark = Color(0xFF1E293B)
val OnSecondaryContainerDark = Color(0xFFE2E8F0)

val TertiaryDark = Color(0xFF2DD4BF)
val TertiaryContainerDark = Color(0xFF134E4A)
val OnTertiaryContainerDark = Color(0xFFCCFBF1)

val ErrorDark = Color(0xFFF87171)
val ErrorContainerDark = Color(0xFF450A0A)
val OnErrorContainerDark = Color(0xFFFEE2E2)

val CanvasDark = Color(0xFF090D16)           // 纯黑深空背景
val SurfaceDark = Color(0xFF111827)          // 深色方块实体
val SurfaceVariantDark = Color(0xFF1F2937)
val InkDark = Color(0xFFF8FAFC)
val MutedInkDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)
val DividerDark = Color(0xFF1E293B)

// ---- 图片上的信息条（纯色，无透明，直角锐利）----
val MediaLabelBg = Color(0xFF0F172A)
val MediaLabelText = Color(0xFFFFFFFF)
val MediaLabelSub = Color(0xFF94A3B8)
