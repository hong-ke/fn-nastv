---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7]
inputDocuments: ['_bmad-output/prd.md', '_bmad-output/analysis/brainstorming-session-2025-12-21T22-54.md']
workflowType: 'architecture'
lastStep: 1
project_name: 'nastv'
user_name: 'Duanhongke'
date: '2025-12-21'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
- **Rendering:** High-performance overlay layer with click-through support.
- **Sync:** ms-level synchronization with ExoPlayer PTS.
- **Interaction:** D-pad Down interception for menu access without blocking native controls.

**Non-Functional Requirements:**
- **Memory:** Strict 15MB limit using Object Pooling.
- **Reliability:** "Circuit Breaker" pattern: FPS < 24 -> Drop packets -> 20s recovery.
- **Robustness:** Silent failure on API timeouts.

**Scale & Complexity:**
- Primary domain: Android TV (Brownfield)
- Complexity level: Medium
- Estimated architectural components: ~4 (DanmakuManager, RenderingEngine, DataProvider, PerformanceMonitor)

### Technical Constraints & Dependencies
- **Android TV:** Focus management is critical; D-pad navigation must be seamless.
- **ExoPlayer:** Must respect existing player state and lifecycle.
- **Legacy Integration:** Must work within `VideoPlayerActivity` without major refactoring.

### Cross-Cutting Concerns Identified
- **Performance Monitoring:** Global FPS observer.
- **Focus Management:** Centralized remote control event dispatching.

## Integration Strategy & Refactoring Plan

### Selected Pattern: Feature Package with "Strangler Fig" Refactoring

**Why this approach?**
The current `app` module contains "God Classes" (`VideoPlayerActivity`: ~1k lines, `DanmuManager`: ~800 lines). Adding more code directly here will increase technical debt. A separate module is overkill for now.

**Strategy:**
1.  **Architecture:** **Feature Package** (`com.mynas.nastv.feature.danmaku`).
2.  **Refactoring Pattern:** **Isolate & Strangle**.
    *   Build new logic alongside old logic.
    *   Gradually migrate functionality.
    *   Delete old code once migration is complete.

### Refactoring Targets

**1. Isolate Core Logic (Clean Room)**
*   Create `core.danmaku` package.
*   Define `IDanmuController` interface to decouple from Activity.

**2. Deconstruct DanmuManager (The Surgery)**
*   **Split Responsibility:**
    *   `DanmuRenderer`: Pure rendering logic (Canvas/View).
    *   `DanmuRepository`: Data fetching and parsing.
    *   `DanmuPresenter`: Business logic and state management.

**3. Cleanup VideoPlayerActivity (The Host)**
*   Remove direct Danmaku implementation details.
*   Interact ONLY via `IDanmuController`.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
1.  **Rendering Engine:** Must ensure < 16ms draw time on low-end hardware.
2.  **Concurrency Model:** Must handle high-frequency updates without checking UI thread.
3.  **Data Serialization:** Must compatibility with existing backend.

### Rendering Architecture

**Decision:** **Custom View (Canvas)**
*   **Rationale:** Simplicity & Performance. We only need specific rendering modes (Scroll/Fix). A 3rd party library brings bloat (animations, user interactions) we explicitly excluded in PRD. Canvas gives us precise control over frame skipping (The Circuit Breaker).
*   **Alternatives Considered:** SurfaceView (Overkill for simple text, sync issues), Jetpack Compose (Performance risk on low-end TV boxes).

### Data & Serialization

**Decision:** **Gson (Existing)**
*   **Rationale:** Consistency. Project already uses Gson heavily in `ApiClient` and `MediaManager`.
*   **Constraint:** Performance NFR (<500ms load) is easily met by Gson for typical subtitle file sizes. No need to add Moshi/Kotlinx Serialization bloat.

### Concurrency Model

**Decision:** **Kotlin Coroutines**
*   **Version:** Existing `androidx.lifecycle.runtime.ktx` (v2.6.1+).
*   **Pattern:**
    *   `Dispatchers.Default` for position calculation.
    *   `Dispatchers.Main` for `invalidate()`.
    *   `Dispatchers.Main` for `invalidate()`.
    *   `viewModelScope` for lifecycle safety.

## Implementation Patterns & Consistency Rules

### Naming Conventions

**1. Class Names**
*   **New Architecture:** Suffix with Component Type (e.g., `DanmuRepository`, `DanmuPresenter`).
*   **Legacy Code:** Prefix with `Legacy` during transition (e.g., `LegacyDanmuManager`).
*   **Interfaces:** Prefix with `I` (e.g., `IDanmuController`) to clearly distinguish contracts from implementations.
*   **Custom Views:** Suffix with `View` (e.g., `DanmakuOverlayView`).

**2. Data Models**
*   All fields MUST use `@SerializedName` to prevent obfuscation issues.
    ```java
    @SerializedName("content")
    public String content;
    ```

### Structural Rules

**1. Package Isolation**
*   **Allowed:** `com.mynas.nastv.feature.danmaku.*`
*   **Forbidden:** Adding logic to `com.mynas.nastv.ui.*` (except for hook points).

**2. Dependency Flow**
*   Features MUST NOT depend on specific Activities.
*   Communication via Interfaces (`IDanmuController`) only.

### Process & Error Handling

**1. Rendering Safety**
*   **Zero-Crash Policy:** Rendering errors (e.g., Canvas OOB) must be caught and logged. They should never crash the main thread.

**2. Migration Policy (User Directive)**
*   **Immediate Replacement:** Upon completion of the new `feature.danmaku` package validation, the legacy `DanmuManager` and related code in `VideoPlayerActivity` must be **deleted immediately**, not deprecated. Clean cutover.

## Project Structure & Boundaries

### Project Directory Tree (App Module)

```text
com.mynas.nastv
├── feature
│   └── danmaku          <-- NEW PACKAGE
│       ├── api          <-- Public API
│       │   └── IDanmuController.kt
│       ├── view         <-- Rendering Layer
│       │   ├── DanmakuOverlayView.kt
│       │   └── internal/DanmuRenderer.kt
│       ├── model        <-- Data Layer
│       │   ├── DanmakuEntity.kt (Gson Model)
│       │   └── DanmuConfig.kt
│       └── logic        <-- Business Logic
│           ├── DanmuRepository.kt
│           └── DanmuPresenter.kt
└── ui
    └── VideoPlayerActivity.java  <-- HOST (Refactored)
```

### Architectural Boundaries

**1. API Boundary (The Firewall)**
*   **Contract:** `IDanmuController` is the ONLY allowed point of interaction for `VideoPlayerActivity`.
*   **Forbidden:** `VideoPlayerActivity` accessing `DanmuPresenter` or `DanmakuOverlayView` directly.

**2. Data Boundary**
*   **Independence:** `DanmuRepository` fetches data independently using `ApiClient`. It does NOT rely on `MediaManager`'s legacy parsing logic.

### Requirements Mapping

*   **FR-01 (Rendering):** Mapped to `view/DanmakuOverlayView.kt` & `view/internal/DanmuRenderer.kt`.
*   **FR-12 (Circuit Breaker):** Implemented in `logic/DanmuPresenter.kt` (monitoring) and `view/internal/DanmuRenderer.kt` (frame skipping).
*   **FR-12 (Circuit Breaker):** Implemented in `logic/DanmuPresenter.kt` (monitoring) and `view/internal/DanmuRenderer.kt` (frame skipping).
*   **NFR-03 (Memory):** Object pool implemented in `view/internal/DanmakuEntityPool.kt` (implicitly part of view/internal).

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
The stack (Custom View + Gson + Coroutines) is internally consistent. No conflicts found between the lightweight rendering approach and the standard Android networking/concurrency libraries.

**Readiness:**
The distinct package structure (`com.mynas.nastv.feature.danmaku`) and strict "Strangler Fig" rules provide a clear path for implementation without destabilizing the existing codebase.

### Requirements Coverage ✅

*   **Rendering:** Covered by `DanmakuOverlayView` (FR-01, FR-02, FR-03).
*   **Integration:** Covered by `DanmuRepository` & `IDanmuController` (FR-04 to FR-07).
*   **Interaction:** Covered by `IDanmuController` contract (FR-08 to FR-10).
*   **Performance:** Covered by Custom View architecture & Object Pooling pattern (FR-11 to FR-13, NFR-01 to NFR-03).
*   **Reliability:** Covered by Circuit Breaker logic in `DanmuPresenter` (NFR-04, NFR-05).

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** High

**Key Strengths:**
1.  **Isolation:** New code is protected from legacy debt.
2.  **Simplicity:** avoided over-engineering (no new modules, no complex 3rd party rendering libs).
3.  **Performance:** "Metal-level" control via Canvas for low-end device support.

**Identified Gaps:**
*   **Linting:** No automated enforcement of the "No UI dependency" rule. Relies on discipline/code review.

### Implementation Handoff

**First Implementation Priority:**
Create the package structure and the `IDanmuController` interface to establish the "Firewall".
