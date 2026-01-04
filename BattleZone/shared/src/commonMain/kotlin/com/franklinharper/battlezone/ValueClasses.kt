package com.franklinharper.battlezone

/**
 * Type-safe wrappers for IDs and counts
 *
 * Note: These are defined as typealias for Kotlin Multiplatform compatibility.
 * For JVM-only projects, consider using value classes with @JvmInline for zero-overhead wrappers.
 *
 * Benefits of these type aliases:
 * - Improved code readability
 * - Self-documenting API
 * - Easier refactoring to value classes in the future
 */

typealias TerritoryId = Int
typealias PlayerId = Int
typealias CellIndex = Int
typealias ArmyCount = Int
