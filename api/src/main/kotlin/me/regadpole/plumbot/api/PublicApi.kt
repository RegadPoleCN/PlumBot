// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api

/** 公开契约注解：`@PublicApi` / `@StableApi` 承诺跨版本兼容，`@ExperimentalApi` 需调用方 opt-in。 */
@PublicApi
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
 * 标记实验性 API：基于 [RequiresOptIn]，调用方必须显式 `@OptIn(ExperimentalApi::class)`，
 * 否则编译器发出警告（Level.WARNING，非错误）。
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "这是 PlumBot 的实验性 API，未来版本可能发生变更，请谨慎在生产环境使用。"
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
annotation class ExperimentalApi
