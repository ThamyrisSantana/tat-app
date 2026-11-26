package br.vendas.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/*
 * Screen transitions. Every screen draws its own opaque background (see
 * VendasApp), so a screen moving over another never shows both mixed.
 *
 * - Opening a screen: it slides in from the right over the current one, which
 *   drifts a little to the left (parallax).
 * - Going back (button or predictive back gesture): the screen slides out to
 *   the right and uncovers the previous one, already fully drawn underneath.
 *   This works while the gesture scrubs the animation: nothing fades to blank.
 * - Between bottom-bar sections: the new section fades in over the old one,
 *   which stays visible until the fade ends.
 *
 * Navigation draws the screen being popped on top, so going back looks right.
 */

private const val SLIDE_MS = 320
private const val FADE_MS = 220

private val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)

/** The screen underneath moves a quarter of the width, for depth. */
private val parallax: (Int) -> Int = { width -> width / 4 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isSectionSwitch(sections: List<KClass<*>>): Boolean =
    sections.any { initialState.destination.hasRoute(it) } && sections.any { targetState.destination.hasRoute(it) }

fun AnimatedContentTransitionScope<NavBackStackEntry>.enterScreen(sections: List<KClass<*>>): EnterTransition =
    if (isSectionSwitch(sections)) {
        fadeIn(tween(FADE_MS))
    } else {
        slideInHorizontally(slideSpec) { width -> width }
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.exitScreen(sections: List<KClass<*>>): ExitTransition =
    if (isSectionSwitch(sections)) {
        ExitTransition.KeepUntilTransitionsFinished
    } else {
        slideOutHorizontally(slideSpec) { width -> -parallax(width) }
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterScreen(sections: List<KClass<*>>): EnterTransition =
    if (isSectionSwitch(sections)) {
        EnterTransition.None
    } else {
        slideInHorizontally(slideSpec) { width -> -parallax(width) }
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitScreen(sections: List<KClass<*>>): ExitTransition =
    if (isSectionSwitch(sections)) {
        fadeOut(tween(FADE_MS))
    } else {
        slideOutHorizontally(slideSpec) { width -> width }
    }
