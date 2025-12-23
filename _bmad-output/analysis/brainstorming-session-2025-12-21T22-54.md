---
stepsCompleted: [1]
inputDocuments: []
session_topic: 'Analyze fnos-tv/web to implement Danmaku on nastv Android TV for private resources'
session_goals: 'Complete full Android TV private resource playback with Danmaku capability'
selected_approach: 'ai-recommended'
techniques_used: ['Trait Transfer', 'Constraint Mapping', 'SCAMPER']
stepsCompleted: [1, 2, 3, 4]
session_active: false
workflow_completed: true
inputDocuments: []
session_topic: 'Analyze fnos-tv/web to implement Danmaku on nastv Android TV for private resources'
session_goals: 'Complete full Android TV private resource playback with Danmaku capability'
ideas_generated: []
context_file: '{project-root}/_bmad/bmm/data/project-context-template.md'
---

## Session Overview

**Topic:** Analyze fnos-tv/web code and interfaces to optimize nastv for Danmaku playback on Android TV.
**Goals:** meaningful integration of private resource playback with full Danmaku support on Android TV.

### Context Guidance

This session focuses on bridging the gap between an existing Python backend (`fnos-tv`), a Vue.js frontend (`fnos-tv-web`), and an Android TV client (`nastv`). Key areas include API analysis, data structure mapping, and Android UI/UX optimization for TV interfaces.

### Session Setup

We are defining a clear path to port web-based Danmaku features to a native Android TV experience. The focus is on technical feasibility, API compatibility, and user experience on a large screen.

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Analyze fnos-tv/web to implement Danmaku on nastv Android TV for private resources

**Recommended Techniques:**

- **Trait Transfer:** Extract core Danmaku features (sync, rendering, style) from fnos-tv-web to ensure faithful porting.
- **Constraint Mapping:** Identify Android TV specific limitations (remote control, focus, performance) to plan mitigations.
- **SCAMPER:** Adapt and optimize the Web interaction model for the TV environment (Substitute, Combine, Adapt, etc.).

**AI Rationale:** This sequence ensures we first understand the "what" (Trait Transfer), then the "where" (Constraint Mapping), and finally the "how" (SCAMPER) to innovative adaptation.

## Technique Execution Results

**Trait Transfer:**

- **Interactive Focus:** Extracting core Danmaku experience for TV context.
- **Key Breakthroughs:**
    - **Synchronization:** Prioritize **Visual Smoothness** over absolute timestamp accuracy to avoid jitter on large screens.
    - **Interaction:** **Read-only Minimalism**. Abandon complex mouse interactions in favor of a passive, cinematic experience.
    - **Visual Density:** **Smart Filtering**. Use algorithms to reduce content density instead of just layout adjustments, ensuring readability without clutter.
- **User Creative Strengths:** Strong decisiveness on UX trade-offs (Performance > Sync, Content > Interaction).

**Constraint Mapping:**

- **Interactive Focus:** Identifying and mitigating Android TV hardware and interaction limitations.
- **Key Breakthroughs:**
    - **Control Scheme:** **Unified Menu**. Integrate Danmaku controls into the existing OSD (On-Screen Display) to maintain interaction consistency, accepting slightly longer user paths.
    - **Performance:** **Drop Danmu Strategy**. "Video First" principle. When system load peaks, aggressively discard Danmaku packets to ensure video playback never stutters.
- **User Creative Strengths:** pragmatic approach to hardware limitations, prioritizing core media experience.

**SCAMPER (Modify):**

- **Interactive Focus:** Refining visual defaults for the "Cinema" context.
- **Key Breakthroughs:**
    - **Visual Defaults:** **Cinema Mode (Option A)**. Default to smaller fonts (30-36sp) and lower opacity (60%) to minimize distraction. The philosophy is "Danmaku as Ambience" rather than "Danmaku as Content".
- **User Creative Strengths:** Clear vision for a premium, non-intrusive viewing experience.

## Technique Execution Complete

We have successfully defined the product philosophy: **A smooth, passive, unobtrusive, and performance-first Danmaku experience.**

## Idea Organization and Action Plan

**Strategic Pillars:** Immersive, High-Performance, Read-Only Experience.

### 1. Interaction Specification
*   **Mode:** **Read-only**. No mouse interaction support.
*   **Controls:** **Unified OSD Integration**. Danmaku settings nested within the existing player menu.
*   **Focus Logic:** Minimal pathing via D-pad (Down key access).

### 2. Performance Strategy
*   **Rendering Priority:** **Visual Smoothness > Time Sync**. Use interpolation to mask jitter.
*   **Circuit Breaker:** **Video First**. Drop Danmaku packets immediately if frame rate drops to preserve video playback.
*   **Smart Filtering:** Server/Client side filtering of high-density content based on weight/priority.

### 3. Visual Standard (Cinema Mode)
*   **Font Size:** **30-36sp** (Subtle readability).
*   **Opacity:** **60%** (Low distraction).
*   **Zoning:** Top **25-33%** screen area only.

## Session Summary

**Key Achievements:**
*   Successfully translated Web-based heavy interaction concepts to a TV-friendly lean-back experience.
*   Defined critical performance boundaries for low-end Android TV hardware.
*   Established a "Cinema Mode" visual standard that aligns with premium viewing goals.

**Next Steps:**
1.  **Backend:** Implement `Smart Filtering` API parameter (optional but recommended).
2.  **Android:** Implement `DanmuManager` with `Choreographer` based rendering loop (not Timer).
3.  **Android:** Build `DanmuSettingsDialog` fragment integrated into `VideoPlayerActivity`.

