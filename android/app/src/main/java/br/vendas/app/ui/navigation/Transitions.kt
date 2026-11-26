package br.vendas.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/*
 * Screen transitions following Material motion. The library default is a
 * 700 ms crossfade in which both screens show half-transparent on top of each
 * other; here the outgoing screen fades out quickly before the incoming one
 * fades in, so the two never overlap.
 *
 * - Between bottom-bar sections: "fade through" (fade out, then fade in with a
 *   slight zoom).
 * - Opening or closing a screen: "shared axis X" (a short horizontal slide).
 */

private const val DURATION_MS = 300
private const val OUT_MS = 90
private const val IN_MS = DURATION_MS - OUT_MS

/** Slide distance: a tenth of the width, enough to show direction without a full page swipe. */
private val slide: (Int) -> Int = { width -> width / 10 }

private val fadeInLate = fadeIn(tween(IN_MS, delayMillis = OUT_MS, easing = LinearOutSlowInEasing))
private val fadeOutEarly = fadeOut(tween(OUT_MS, easing = FastOutLinearInEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isSectionSwitch(sections: List<KClass<*>>): Boolean =
    sections.any { initialState.destination.hasRoute(it) } && sections.any { targetState.destination.hasRoute(it) }

fun AnimatedContentTransitionScope<NavBackStackEntry>.enterScreen(sections: List<KClass<*>>): EnterTransition =
    if (isSectionSwitch(sections)) {
        fadeInLate + scaleIn(tween(IN_MS, delayMillis = OUT_MS, easing = LinearOutSlowInEasing), initialScale = 0.96f)
    } else {
        slideInHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing), slide) + fadeInLate
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.exitScreen(sections: List<KClass<*>>): ExitTransition =
    if (isSectionSwitch(sections)) {
        fadeOutEarly
    } else {
        slideOutHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { -slide(it) } + fadeOutEarly
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterScreen(sections: List<KClass<*>>): EnterTransition =
    if (isSectionSwitch(sections)) {
        enterScreen(sections)
    } else {
        slideInHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { -slide(it) } + fadeInLate
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitScreen(sections: List<KClass<*>>): ExitTransition =
    if (isSectionSwitch(sections)) {
        fadeOutEarly
    } else {
        slideOutHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing), slide) + fadeOutEarly
    }
