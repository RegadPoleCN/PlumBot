package me.regadpole.plumbot.api

/**
 * Public API contract annotations for PlumBot.
 */

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
 * 标记该 API 处于实验性孵化阶段。
 * 基于 Kotlin 官方标准 [RequiresOptIn]，调用方在编译期必须显式添加 `@OptIn(ExperimentalApi::class)`，
 * 否则 Kotlin 编译器将直接发出编译警告。
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
