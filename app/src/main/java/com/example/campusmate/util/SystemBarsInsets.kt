package com.example.campusmate.util

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.campusmate.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.max

/** Applies edge-to-edge system bar insets to the existing XML/View screens. */
object SystemBarsInsets {
    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        if (root.getTag(R.id.tag_system_bars_insets_applied) == true) return
        root.setTag(R.id.tag_system_bars_insets_applied, true)
        apply(root)
    }

    private fun apply(root: View) {
        val topTarget = findTopInsetTarget(root)?.takeIf { it !== root }
        val bottomNavigation = findFirst(root, BottomNavigationView::class.java)
        val rootState = root.captureState()
        val topTargetState = topTarget?.captureState()
        val bottomNavigationState = bottomNavigation?.captureState()
        var realInsetsApplied = false

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val topInset = max(max(systemBars.top, displayCutout.top), topProtectionHeight(root.resources))
            val bottomInset = max(max(max(systemBars.bottom, displayCutout.bottom), ime.bottom), navigationBarHeight(root.resources))

            applyInsets(
                root = root,
                rootState = rootState,
                topTarget = topTarget,
                topTargetState = topTargetState,
                bottomNavigation = bottomNavigation,
                bottomNavigationState = bottomNavigationState,
                topInset = topInset,
                bottomInset = bottomInset
            )
            realInsetsApplied = true
            insets
        }
        requestInsetsWhenReady(root)
        root.post {
            if (!realInsetsApplied) {
                applyInsets(
                    root = root,
                    rootState = rootState,
                    topTarget = topTarget,
                    topTargetState = topTargetState,
                    bottomNavigation = bottomNavigation,
                    bottomNavigationState = bottomNavigationState,
                    topInset = topProtectionHeight(root.resources),
                    bottomInset = navigationBarHeight(root.resources)
                )
            }
        }
    }

    private fun applyInsets(
        root: View,
        rootState: ViewState,
        topTarget: View?,
        topTargetState: ViewState?,
        bottomNavigation: BottomNavigationView?,
        bottomNavigationState: ViewState?,
        topInset: Int,
        bottomInset: Int
    ) {
        root.applyPadding(
            rootState,
            top = rootState.top + if (topTarget == null) topInset else 0,
            bottom = rootState.bottom + if (bottomNavigation == null) bottomInset else 0
        )
        topTargetState?.let { state ->
            topTarget?.applyPadding(state, top = state.top + topInset)
            topTarget?.applyHeight(state, topInset)
        }
        bottomNavigationState?.let { state ->
            bottomNavigation?.applyPadding(state, bottom = state.bottom + bottomInset)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestInsetsWhenReady(root: View) {
        if (ViewCompat.isAttachedToWindow(root)) {
            ViewCompat.requestApplyInsets(root)
            return
        }
        root.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(view)
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            }
        )
    }

    private fun findTopInsetTarget(root: View): View? {
        return findFirst(root, AppBarLayout::class.java)
            ?: findFirst(root, MaterialToolbar::class.java)
    }

    private fun <T : View> findFirst(view: View, clazz: Class<T>): T? {
        if (clazz.isInstance(view)) {
            @Suppress("UNCHECKED_CAST")
            return view as T
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findFirst(view.getChildAt(index), clazz)?.let { return it }
            }
        }
        return null
    }

    private fun View.captureState(): ViewState {
        return ViewState(
            left = paddingLeft,
            top = paddingTop,
            right = paddingRight,
            bottom = paddingBottom,
            height = layoutParams?.height ?: 0
        )
    }

    private fun View.applyPadding(
        state: ViewState,
        top: Int = state.top,
        bottom: Int = state.bottom
    ) {
        setPadding(state.left, top, state.right, bottom)
    }

    private fun View.applyHeight(state: ViewState, topInset: Int) {
        if (state.height <= 0) return
        val params = layoutParams ?: return
        params.height = state.height + topInset
        layoutParams = params
    }

    private fun statusBarHeight(resources: Resources): Int {
        return systemDimension(resources, "status_bar_height")
    }

    private fun navigationBarHeight(resources: Resources): Int {
        return systemDimension(resources, "navigation_bar_height")
    }

    private fun topProtectionHeight(resources: Resources): Int {
        return max(
            statusBarHeight(resources),
            resources.getDimensionPixelSize(R.dimen.system_bar_top_protection)
        )
    }

    private fun systemDimension(resources: Resources, name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private data class ViewState(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val height: Int
    )
}
