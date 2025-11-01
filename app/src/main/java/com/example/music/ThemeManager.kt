package com.arotter.music

import android.content.Context
import android.graphics.Color

object ThemeManager {
    private const val PREFS = "app_theme"
    private const val KEY_PRIMARY = "primary_color"
    private const val KEY_PRIMARY_GRADIENT_START = "primary_gradient_start"
    private const val KEY_PRIMARY_GRADIENT_END = "primary_gradient_end"
    private const val KEY_SECONDARY = "secondary_color"
    private const val KEY_ACCENT = "accent_color"

    const val ACTION_THEME_CHANGED = "com.arotter.music.THEME_CHANGED"

    // Defaults: primary black, secondary white, accent orange
    private const val DEFAULT_PRIMARY = 0xFF000000.toInt()
    private const val DEFAULT_PRIMARY_GRADIENT_START = 0xFF000000.toInt()
    private const val DEFAULT_PRIMARY_GRADIENT_END = 0xFF000000.toInt()
    private const val DEFAULT_SECONDARY = 0xFFFFFFFF.toInt()
    private const val DEFAULT_ACCENT = 0xFFFFA500.toInt()

    fun getPrimaryColor(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PRIMARY, DEFAULT_PRIMARY)

    fun getPrimaryGradientStart(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PRIMARY_GRADIENT_START, DEFAULT_PRIMARY_GRADIENT_START)

    fun getPrimaryGradientEnd(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PRIMARY_GRADIENT_END, DEFAULT_PRIMARY_GRADIENT_END)

    fun getSecondaryColor(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SECONDARY, DEFAULT_SECONDARY)

    fun getAccentColor(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, DEFAULT_ACCENT)

    fun setPrimaryColor(ctx: Context, color: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PRIMARY, color).apply()
    }

    fun setPrimaryGradientStart(ctx: Context, color: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PRIMARY_GRADIENT_START, color).apply()
    }

    fun setPrimaryGradientEnd(ctx: Context, color: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PRIMARY_GRADIENT_END, color).apply()
    }

    fun setSecondaryColor(ctx: Context, color: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SECONDARY, color).apply()
    }

    fun setAccentColor(ctx: Context, color: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACCENT, color).apply()
    }

    fun parseHexOrNull(hex: String): Int? = try {
        Color.parseColor(if (hex.startsWith("#")) hex else "#" + hex)
    } catch (e: Exception) { null }

    fun applyStatusBar(window: android.view.Window, ctx: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val statusColor = getPrimaryGradientStart(ctx)
            window.statusBarColor = statusColor
            // Навбар тоже под тему (по желанию)
            try { window.navigationBarColor = getPrimaryColor(ctx) } catch (_: Exception) {}

            // Контраст иконок статус-бара
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val secondary = getSecondaryColor(ctx)
                val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(secondary)
                val decor = window.decorView
                var vis = decor.systemUiVisibility
                vis = if (luminance > 0.5) {
                    vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                decor.systemUiVisibility = vis
            }
        }
    }

    fun applyTransparentStatusBar(window: android.view.Window, darkIcons: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val decor = window.decorView
                var vis = decor.systemUiVisibility
                vis = if (darkIcons) {
                    vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                decor.systemUiVisibility = vis
            }
        }
    }

    fun applyTransparentStatusBarWithBackground(window: android.view.Window, darkIcons: Boolean, ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Устанавливаем цвет статус-бара в цвет фона приложения
            val backgroundColor = getPrimaryGradientStart(ctx)
            window.statusBarColor = backgroundColor
            // Установим цвет нижней панели навигации как основной фон
            try { window.navigationBarColor = getPrimaryColor(ctx) } catch (_: Exception) {}

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val decor = window.decorView
                var vis = decor.systemUiVisibility
                vis = if (darkIcons) {
                    vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                decor.systemUiVisibility = vis
            }
            // Светлые иконки для нав-бара (API 27+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                val decor = window.decorView
                var vis = decor.systemUiVisibility
                val lightNav = androidx.core.graphics.ColorUtils.calculateLuminance(getPrimaryColor(ctx)) > 0.5
                vis = if (lightNav) {
                    vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
                decor.systemUiVisibility = vis
            }
        }
        // На старых версиях Android (< Lollipop) статус-бар не прозрачный,
        // но фон приложения будет виден под ним благодаря layout
    }

    // Показывает системные панели, убирает immersive/overlay и красит бары под фон приложения
    fun showSystemBars(window: android.view.Window, ctx: android.content.Context) {
        val decor = window.decorView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = decor.windowInsetsController
            controller?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            run {
                var vis = decor.systemUiVisibility
                vis = vis and android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
                vis = vis and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
                vis = vis and android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
                vis = vis and android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN.inv()
                vis = vis and android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION.inv()
                decor.systemUiVisibility = vis
            }
        }
        // Не перекрашиваем цвета баров здесь, чтобы не затирать цвет экрана
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val secondary = getSecondaryColor(ctx)
            val lightStatus = androidx.core.graphics.ColorUtils.calculateLuminance(secondary) > 0.5
            var vis = decor.systemUiVisibility
            vis = if (lightStatus) vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            // Light nav bar (API 27+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                val lightNav = androidx.core.graphics.ColorUtils.calculateLuminance(getPrimaryColor(ctx)) > 0.5
                vis = if (lightNav) vis or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                else vis and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            decor.systemUiVisibility = vis
        }
    }

    fun enableImmersive(window: android.view.Window) {
        val decor = window.decorView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = decor.windowInsetsController
            controller?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }
}
