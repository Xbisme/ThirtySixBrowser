package com.raumanian.thirtysix.browser.domain.repository

/*
 * Spec 018 — the ONE addition to an existing interface.
 *
 * `SettingsRepository` today exposes a single observer and five setters. It has no way to
 * read the current settings once and be done, and Spec 018 needs exactly that: the start-up
 * decision must know whether onboarding is complete BEFORE the navigation graph is built
 * (research.md R2, data-model.md INV-5).
 *
 * Why the existing observer will not do:
 *
 *   observeUserSettings().collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
 *
 * starts at UserSettings.DEFAULT, whose isOnboardingCompleted is `false`
 * (AppDefaults.IS_ONBOARDING_COMPLETED, verified). Composing the graph against that first
 * emission routes EVERY launch to onboarding, including a user who finished months ago — and
 * because NavHost captures startDestination at first composition, that is not a one-frame
 * flash but the graph's actual start route. FR-013 fails for returning users and FR-014 is
 * inverted.
 *
 * This is the single most likely way to get Spec 018 wrong.
 */

interface SettingsRepositoryAmendment {

    /**
     * The current settings, read once.
     *
     * Suspends until a real value is available; never returns [UserSettings.DEFAULT] as a
     * placeholder. Callers that need to decide something before composing UI use this;
     * callers that need to follow changes keep using `observeSettings()`.
     *
     * Contract:
     *  - MUST return the persisted snapshot, not the documented defaults, when one exists.
     *  - MUST return the documented defaults on a fresh install, where that IS the truth.
     *  - MUST NOT be called on the main thread by the implementation's own doing; it is a
     *    suspend function and the caller awaits it off the main dispatcher.
     */
    // suspend fun currentSettings(): UserSettings
}

/*
 * Rejected alternatives, recorded so they are not revisited:
 *
 *  - Hold the splash screen open until the flag arrives, via setKeepOnScreenCondition.
 *    REJECTED: Spec 017's INV-12 forbids that call outright. Its absence is what makes Spec
 *    017's FR-008 true — adding one is the one way to reintroduce an artificial launch delay,
 *    and it would put a disk read on the launch critical path that Spec 017's SC-004 measures.
 *
 *  - Read the flag synchronously on the main thread before setContent.
 *    REJECTED: disk I/O on the main thread at launch.
 *
 *  - Compose a neutral placeholder screen until the flag arrives, then swap the graph.
 *    VIABLE but rejected: a third visual state between the launch screen and the app, for a
 *    read measured in microseconds. Kept as the fallback if awaiting proves awkward.
 *
 *  - Give the flow its own DataStore read, bypassing the repository.
 *    REJECTED: Constitution §IV — a ViewModel reading DataStore directly. The read belongs
 *    behind the repository like every other settings access in this app.
 */
