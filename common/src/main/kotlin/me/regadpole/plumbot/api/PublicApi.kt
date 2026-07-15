package me.regadpole.plumbot.api

/**
 * Public API contract annotations for PlumBot.
 *
 * These markers exist to make the **public API surface** explicit to third-party
 * plugin authors. A class / function / field marked with one of these annotations
 * is part of the documented API. Types **without** any of these annotations in
 * `common/.../api` / `common/.../bot` / `common/.../platform` / `common/.../task`
 * are considered internal even if Kotlin visibility is `public` by default.
 *
 * Companion docs:
 *  - `coding-docs/README.md` (public API list)
 *  - `coding-docs/development/extending-from-other-plugins.md` (usage guide)
 */

/**
 * Basic public API marker. Third-party plugins MAY use, but no stability
 * guarantee beyond the next minor version is given.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
annotation class PublicApi

/**
 * Stable API marker. Signatures and behavior are committed to be backward
 * compatible across minor versions. Major versions MAY break, but breaking
 * changes will be listed in release notes.
 *
 * Modifying a `@StableApi` type requires a synchronous update of
 * `coding-docs/README.md` public API list.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
annotation class StableApi

/**
 * Experimental / preview marker. The API MAY change in the next minor version
 * without notice. Third-party plugins should opt-in explicitly.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
annotation class ExperimentalApi
