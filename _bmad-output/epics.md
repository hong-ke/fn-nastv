---
stepsCompleted: [1, 2, 3]
inputDocuments: ['_bmad-output/prd.md', '_bmad-output/architecture.md']
---

# nastv - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for nastv, decomposing the requirements from the PRD and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

- **FR-01:** 播放器必须能在视频画面之上渲染弹幕层，支持"滚动 (Scroll)"与"顶部/底部固定 (Fix)"三种类型。
- **FR-02:** 系统必须默认启用"影院模式"预设：渲染区域限制在屏幕顶部 30%，透明度为 50%。
- **FR-03:** 弹幕字体大小应自动适配屏幕分辨率（1080p/4k 保持视觉一致）。
- **FR-04:** 客户端必须能根据当前视频 ID 向后端 `/danmu/get` 接口请求数据。
- **FR-05:** 客户端必须能处理后端返回的 JSON 数据格式并解析为弹幕对象。
- **FR-06:** 播放器必须能根据视频进度时间戳 (PTS) 同步显示对应的弹幕。
- **FR-07:** 播放器跳转进度 (Seek) 时，弹幕必须同步跳转到对应时间点。
- **FR-08:** 用户在全屏播放状态下按下遥控器 `D-pad Down` 键，必须呼出带有"弹幕设置"按钮的原生控制条。
- **FR-09:** 用户通过"弹幕设置"按钮可打开侧边栏/浮层菜单，包含"弹幕开关"。
- **FR-10:** 用户切换弹幕开关状态后，设置应立即生效。
- **FR-11:** 系统必须实时监控当前渲染帧率 (FPS)。
- **FR-12:** 当帧率连续 3 秒低于 24fps 时，系统必须自动触发熔断机制（丢弃新弹幕或清屏）。
- **FR-13:** 弹幕加载耗时应 < 500ms（在网络正常情况下），不应阻塞视频起播。

### NonFunctional Requirements

- **NFR-01 (Start Time):** 弹幕引擎冷启动耗时应 < **500ms**，确保不阻塞视频首帧播放。
- **NFR-02 (FPS):** 在 100 条/屏的高负载下，渲染帧率不低于 **24fps** (视频播放最低流畅标准)。
- **NFR-03 (Memory):** 弹幕对象池(Object Pool) 最大内存占用限制为 **15MB**，防止在低内存设备上触发系统 OOM 杀进程。
- **NFR-04 (Recovery):** 触发视频帧率熔断丢包后，当 CPU 负载回落，系统应在 **20秒内** 自动恢复弹幕显示。
- **NFR-05 (Error Tolerance):** 后端 API 请求超时（>3000ms）或 500 错误时，客户端应静默失败，仅记录日志，不向用户展示错误提示。
- **NFR-06 (Sanitization):** 客户端必须处理弹幕文本中的特殊字符（如换行符炸弹），防止 UI 错位或渲染异常。

### Additional Requirements

**From Architecture:**
- **Integration Strategy:** Feature Package (`com.mynas.nastv.feature.danmaku`) with "Strangler Fig" refactoring pattern
- **Rendering:** Custom View using Canvas for precise performance control
- **Data Serialization:** Gson (existing in project) with mandatory `@SerializedName` annotations
- **Concurrency:** Kotlin Coroutines with `Dispatchers.Default` for calculations and `Dispatchers.Main` for UI updates
- **API Boundary:** `IDanmuController` interface as the ONLY communication point between VideoPlayerActivity and Danmaku feature
- **Zero-Crash Policy:** All rendering errors must be caught and logged, never crash the main thread
- **Legacy Cleanup:** Upon feature completion, legacy `DanmuManager` must be deleted immediately (not deprecated)
- **Package Isolation:** New code confined to `com.mynas.nastv.feature.danmaku.*` only

### FR Coverage Map

**Epic 1: Architectural Foundation**
- Architecture: Feature Package structure
- Architecture: Strangler Fig refactoring pattern
- Architecture: IDanmuController API boundary
- Architecture: Zero-Crash policy
- Architecture: Legacy cleanup directive

**Epic 2: Core Danmaku Rendering**
- FR-01: Render danmaku layer with Scroll/Fixed types
- FR-02: Default Cinema Mode (top 30%, 50% opacity)
- FR-03: Adaptive font sizing for resolution
- FR-04: Fetch data from /danmu/get API
- FR-05: Parse JSON response to danmaku objects
- FR-06: Synchronize with video PTS
- FR-07: Sync on seek/jump

**Epic 3: User Control**
- FR-08: D-pad Down to show OSD with Danmaku button
- FR-09: Settings menu with danmaku toggle
- FR-10: Immediate effect on toggle

**Epic 4: Performance & Reliability**
- FR-11: Real-time FPS monitoring
- FR-12: Circuit breaker at <24fps for 3s
- FR-13: <500ms load time
- NFR-01: <500ms cold start
- NFR-02: >=24fps at 100 items/screen
- NFR-03: <15MB memory (Object Pool)
- NFR-04: 20s auto-recovery after circuit break
- NFR-05: Silent failure on API errors
- NFR-06: Input sanitization

## Epic List

### Epic 1: Architectural Foundation & Refactoring
Establish the technical foundation for the danmaku feature by creating the isolated `feature.danmaku` package structure, defining the API boundary interface, and refactoring existing `VideoPlayerActivity` integration points. This epic enables all future development without destabilizing the legacy codebase.
**FRs covered:** Architecture constraints (Feature Package, Strangler Fig, API Boundary, Zero-Crash)

### Epic 2: Core Danmaku Rendering & Data Integration
Users can see danmaku (bullet comments) scrolling across the video screen while watching content, experiencing a "shared viewing" atmosphere. The system fetches danmaku data from the backend, parses it, and renders it synchronized with video playback.
**FRs covered:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07

### Epic 3: User Control & Interaction
Users can easily control the danmaku experience using the TV remote control. They can access danmaku settings via the on-screen display (OSD) and toggle danmaku on/off instantly without disrupting video playback.
**FRs covered:** FR-08, FR-09, FR-10

### Epic 4: Performance Optimization & Reliability
Even on low-end TV boxes, video playback remains smooth and never crashes due to danmaku rendering. The system monitors performance in real-time and automatically protects video quality by dropping danmaku packets when the device is under heavy load.
**FRs covered:** FR-11, FR-12, FR-13, NFR-01, NFR-02, NFR-03, NFR-04, NFR-05, NFR-06

---

## Epic 1: Architectural Foundation & Refactoring

Establish the technical foundation for the danmaku feature by creating the isolated `feature.danmaku` package structure, defining the API boundary interface, and refactoring existing `VideoPlayerActivity` integration points. This epic enables all future development without destabilizing the legacy codebase.

### Story 1.1: Create Feature Package Structure

As a **developer**,
I want to create the isolated `com.mynas.nastv.feature.danmaku` package structure with all required sub-packages,
So that danmaku implementation code is cleanly separated from the existing UI layer and follows the Strangler Fig pattern.

**Acceptance Criteria:**

**Given** the existing project structure in `app/src/main/java/com/mynas/nastv/`
**When** I create the new feature package
**Then** the following directory structure exists:
- `com.mynas.nastv.feature.danmaku.api/` (public contracts)
- `com.mynas.nastv.feature.danmaku.view/` (rendering components)
- `com.mynas.nastv.feature.danmaku.model/` (data models)
- `com.mynas.nastv.feature.danmaku.logic/` (business logic)

**And** a package-level README.md exists in `feature/danmaku/` documenting:
- Package purpose and architecture decisions
- Dependency rules (no dependencies on `com.mynas.nastv.ui.*`)
- Reference to Architecture.md

**And** the package structure can be built successfully without compilation errors

### Story 1.2: Define IDanmuController API Boundary

As a **developer**,
I want to define the `IDanmuController` interface as the sole communication contract between `VideoPlayerActivity` and the danmaku feature,
So that the danmaku feature is decoupled and can evolve independently without affecting the host activity.

**Acceptance Criteria:**

**Given** the package structure from Story 1.1
**When** I create `IDanmuController.java` in `feature.danmaku.api/`
**Then** the interface includes these methods:
- `void initialize(Context context, ViewGroup parentContainer)`
- `void loadDanmaku(String videoId, String episodeId)`
- `void show()`
- `void hide()`
- `void seekTo(long timestampMs)`
- `void updatePlaybackPosition(long currentPositionMs)`
- `void destroy()`

**And** all method signatures use standard Android/Java types (no custom domain objects in the interface)

**And** comprehensive JavaDoc comments explain each method's purpose, parameters, and threading requirements

**And** the interface compiles without errors

### Story 1.3: Create Core Data Models with Gson Support

As a **developer**,
I want to create `DanmakuEntity.java` and `DanmuConfig.java` data models with proper Gson annotations,
So that danmaku data can be parsed from the backend API and configured by users.

**Acceptance Criteria:**

**Given** the package structure from Story 1.1
**When** I create `DanmakuEntity.java` in `feature.danmaku.model/`
**Then** the class includes these fields with `@SerializedName` annotations:
- `@SerializedName("time") long time` (PTS in milliseconds)
- `@SerializedName("text") String text` (danmaku content)
- `@SerializedName("mode") int mode` (0=scroll, 1=top, 2=bottom)
- `@SerializedName("color") String color` (hex color code)

**And** when I create `DanmuConfig.java` in `feature.danmaku.model/`
**Then** the class includes configuration fields:
- `float opacity` (default 0.5f, range 0.0-1.0)
- `int fontSize` (default calculated from screen density)
- `float topMarginPercent` (default 0.0f)
- `float bottomMarginPercent` (default 0.75f, meaning top 25% visible)
- `boolean enabled` (default true)

**And** both classes implement `Serializable` for potential future state saving

**And** both classes compile and can be instantiated in unit tests

### Story 1.4: Refactor VideoPlayerActivity Integration Point

As a **developer**,
I want to refactor `VideoPlayerActivity.java` to interact with danmaku ONLY through the `IDanmuController` interface,
So that legacy `DanmuManager` references are removed and the new architecture can be integrated without breaking existing functionality.

**Acceptance Criteria:**

**Given** the existing `VideoPlayerActivity.java` with legacy `DanmuManager` references
**When** I refactor the activity
**Then** all direct references to `DanmuManager` are removed (search shows 0 results for "new DanmuManager")

**And** a new field exists: `private IDanmuController danmuController;`

**And** the activity uses a temporary stub implementation:
```java
// TODO: Replace with real implementation in Epic 2
danmuController = new IDanmuController() {
    @Override public void initialize(...) { /* no-op */ }
    @Override public void loadDanmaku(...) { /* no-op */ }
    // ... all other methods as no-ops
};
```

**And** in `onCreate()`, the controller is initialized: `danmuController.initialize(this, videoContainer);`

**And** in `onDestroy()`, the controller is cleaned up: `danmuController.destroy();`

**And** the app compiles, builds, and launches without crashes

**And** video playback works normally (no regressions)

**And** legacy `DanmuManager.java` is renamed to `LegacyDanmuManager.java` (marked for deletion after Epic 2 validation)

---

## Epic 2: Core Danmaku Rendering & Data Integration

Users can see danmaku (bullet comments) scrolling across the video screen while watching content, experiencing a "shared viewing" atmosphere. The system fetches danmaku data from the backend, parses it, and renders it synchronized with video playback.

### Story 2.1: Implement DanmakuOverlayView Custom View

As a **developer**,
I want to create a custom `DanmakuOverlayView` that renders danmaku using Canvas API with focus pass-through,
So that danmaku can be displayed on top of the video without intercepting remote control events.

**Acceptance Criteria:**

**Given** the package structure from Epic 1
**When** I create `DanmakuOverlayView.java` in `feature.danmaku.view/`
**Then** the class extends `View` and overrides `onDraw(Canvas canvas)`

**And** in the constructor, the view is configured with:
- `setFocusable(false)`
- `setClickable(false)`
- `setWillNotDraw(false)` to enable drawing

**And** the view includes a public method `void renderDanmaku(List<DanmakuEntity> visibleItems)` that:
- Accepts a list of danmaku entities to draw in the current frame
- Clears the canvas with `canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)`
- Iterates through items and draws text using `canvas.drawText()`

**And** the view includes a field `private Paint textPaint` configured with:
- Anti-alias enabled
- Text size set based on screen density
- Default color white

**And** when added to a `FrameLayout` and `invalidate()` is called, the `onDraw()` method executes without errors

**And** touch events pass through to the underlying video view (verified via manual testing with D-pad)

### Story 2.2: Implement DanmuRepository Data Layer

As a **developer**,
I want to create `DanmuRepository` to fetch and parse danmaku data from the `/danmu/get` API,
So that the rendering engine has access to properly parsed danmaku entities.

**Acceptance Criteria:**

**Given** the data models from Story 1.3
**When** I create `DanmuRepository.java` in `feature.danmaku.logic/`
**Then** the class includes method: `void fetchDanmaku(String videoId, String episodeId, RepositoryCallback callback)`

**And** the method constructs the API URL: `/danmu/get?guid={videoId}&episode_guid={episodeId}`

**And** the method uses existing `ApiClient` or `OkHttp` for network call (follows project patterns)

**And** on successful response (HTTP 200), the JSON is parsed using Gson:
```java
Type listType = new TypeToken<Map<String, List<DanmakuEntity>>>(){}.getType();
Map<String, List<DanmakuEntity>> danmakuMap = gson.fromJson(jsonString, listType);
```

**And** the parsed data is returned via callback: `callback.onSuccess(danmakuMap)`

**And** on failure (timeout >3000ms or HTTP 500), the error is logged but NOT shown to user (NFR-05):
```java
Log.e(TAG, "Danmaku fetch failed", exception);
callback.onError(exception);
```

**And** the class includes a callback interface:
```java
interface RepositoryCallback {
    void onSuccess(Map<String, List<DanmakuEntity>> data);
    void onError(Exception e);
}
```

**And** unit tests verify Gson parsing with sample JSON from the actual API

### Story 2.3: Implement DanmuRenderer Rendering Engine

As a **developer**,
I want to create `DanmuRenderer` that applies Cinema Mode styling and handles scroll/fixed danmaku types,
So that danmaku is displayed according to FR-01, FR-02, and FR-03 specifications.

**Acceptance Criteria:**

**Given** `DanmakuOverlayView` and `DanmuConfig` from previous stories
**When** I create `DanmuRenderer.java` in `feature.danmaku.view/`
**Then** the class includes method: `List<DanmakuEntity> calculateVisibleDanmaku(long currentPositionMs, int viewWidth, int viewHeight, DanmuConfig config)`

**And** the method filters danmaku by mode (FR-01):
- Mode 0 (Scroll): Calculates horizontal position based on timestamp and speed
- Mode 1 (Top): Fixed at top of visible area
- Mode 2 (Bottom): Fixed at bottom of visible area

**And** Cinema Mode is applied (FR-02):
- Rendering area: top 30% of screen (viewHeight * 0.3)
- Danmaku outside this area are filtered out
- Opacity: Applied via `textPaint.setAlpha((int)(255 * config.opacity))`

**And** font size is adaptive (FR-03):
```java
DisplayMetrics metrics = context.getResources().getDisplayMetrics();
float baseSize = metrics.densityDpi >= DisplayMetrics.DENSITY_XXHIGH ? 48 : 36;
int fontSize = (int)(baseSize * (viewHeight / 1080.0)); // Scale to resolution
```

**And** for scroll-type danmaku, the position calculation is:
```java
long elapsed = currentPositionMs - danmaku.time;
float speed = 200; // pixels per second
float x = viewWidth - (elapsed / 1000.0f) * speed;
// Visible if x is within [0, viewWidth]
```

**And** the method returns only danmaku currently visible on screen (performance optimization)

**And** unit tests verify correct filtering and positioning for 1080p and 4K resolutions

### Story 2.4: Implement PTS Synchronization Logic

As a **developer**,
I want to implement precise synchronization between ExoPlayer position and danmaku rendering,
So that danmaku appears at the correct timestamps and responds to seek operations (FR-06, FR-07).

**Acceptance Criteria:**

**Given** `DanmuRenderer` and access to ExoPlayer instance
**When** I create `DanmuPresenter.java` in `feature.danmaku.logic/`
**Then** the class includes a method: `void onPlaybackPositionUpdate(long currentPositionMs)`

**And** this method is called from `VideoPlayerActivity` using ExoPlayer's position listener:
```java
exoPlayer.addListener(new Player.Listener() {
    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY) {
            handler.post(updateRunnable); // updates every 100ms
        }
    }
});
```

**And** on position update, `DanmuPresenter` calls `DanmuRenderer.calculateVisibleDanmaku(currentPositionMs, ...)`

**And** the result is passed to `DanmakuOverlayView.renderDanmaku(visibleList)`

**And** when user seeks (FR-07), the presenter handles:
```java
void onSeek(long newPositionMs) {
    currentLoadedPosition = newPositionMs;
    // Clear old danmaku from display
    overlayView.renderDanmaku(Collections.emptyList());
    // Recalculate from new position
    onPlaybackPositionUpdate(newPositionMs);
}
```

**And** synchronization accuracy is verified: danmaku appears within ±100ms of specified timestamp

**And** seek operations complete within 200ms (no visible lag)

### Story 2.5: Integrate DanmuControllerImpl into VideoPlayerActivity

As a **developer**,
I want to create `DanmuControllerImpl` that orchestrates all components and replace the stub in `VideoPlayerActivity`,
So that the end-to-end flow works: fetch → parse → render → sync.

**Acceptance Criteria:**

**Given** all components from Stories 2.1-2.4
**When** I create `DanmuControllerImpl.java` in `feature.danmaku.logic/`
**Then** the class implements `IDanmuController` interface

**And** the `initialize()` method:
- Creates `DanmakuOverlayView` and adds it to `parentContainer`
- Initializes `DanmuRepository`, `DanmuRenderer`, `DanmuPresenter`
- Sets up default `DanmuConfig` (Cinema Mode enabled)

**And** the `loadDanmaku(videoId, episodeId)` method:
- Calls `repository.fetchDanmaku()` asynchronously
- On success, stores the data in `DanmuPresenter`
- Logs success/failure (NFR-05: silent failure)

**And** the `updatePlaybackPosition(currentPositionMs)` method:
- Delegates to `presenter.onPlaybackPositionUpdate()`
- Triggers `overlayView.invalidate()` to redraw

**And** the `seekTo(timestampMs)` method:
- Delegates to `presenter.onSeek()`

**And** in `VideoPlayerActivity.onCreate()`, replace stub:
```java
// OLD: danmuController = new IDanmuController() { stub }
// NEW:
danmuController = new DanmuControllerImpl();
danmuController.initialize(this, videoContainer);
```

**And** wire up ExoPlayer callbacks:
```java
exoPlayer.addListener(new Player.Listener() {
    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) startPositionUpdates();
        else stopPositionUpdates();
    }
});
```

**And** end-to-end test:
1. Launch app, play a video with known danmaku
2. Danmaku appears scrolling across the screen
3. Seek to different timestamp → danmaku updates instantly
4. Danmaku renders in top 30% only (Cinema Mode)
5. Video playback remains smooth (no frame drops)

**And** load time is <500ms from API call to first render (NFR-01)

---

## Epic 3: User Control & Interaction

Users can easily control the danmaku experience using the TV remote control. They can access danmaku settings via the on-screen display (OSD) and toggle danmaku on/off instantly without disrupting video playback.

### Story 3.1: Add Danmaku Settings Entry to OSD Control Bar

As a **user**,
I want to see a "弹幕设置" button in the video player's on-screen display (OSD) when I press D-pad Down,
So that I can access danmaku controls without leaving the video playback screen.

**Acceptance Criteria:**

**Given** the video player is in fullscreen playback mode
**When** I press the **D-pad Down** key on the remote control (FR-08)
**Then** the native control bar (OSD) appears showing playback controls (play/pause, progress bar, etc.)

**And** a new focusable button labeled "弹幕设置" is visible in the control bar
- Position: Adjacent to existing settings/quality buttons
- Icon: A recognizable danmaku icon (e.g., comment bubble with lines)
- State: Visually highlighted when focused via D-pad navigation

**And** the button has proper focus handling:
```java
danmakuSettingsButton.setFocusable(true);
danmakuSettingsButton.setOnKeyListener((v, keyCode, event) -> {
    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
        openDanmakuSettingsPanel();
        return true;
    }
    return false;
});
```

**And** when I navigate to the button using D-pad and press **Center/OK**, the button click event triggers

**And** accessibility is supported:
- Button has `contentDescription="弹幕设置"`
- Clear visual focus indicator (highlight border or background change)

**And** the OSD layout adapts gracefully (no overlapping buttons on different screen sizes)

### Story 3.2: Implement Danmaku Settings Panel UI

As a **user**,
I want to open a settings panel with a danmaku toggle when I click the "弹幕设置" button,
So that I can turn danmaku on or off according to my preference (FR-09).

**Acceptance Criteria:**

**Given** the "弹幕设置" button from Story 3.1
**When** I press Center/OK on the button
**Then** a modal panel/dialog appears on screen

**And** the panel UI includes:
- Title: "弹幕设置"
- Primary control: A toggle switch labeled "显示弹幕" with current state (ON/OFF)
- Visual design: Follows Android TV Leanback design patterns
- Positioning: Centered or anchored to the right side of the screen

**And** the toggle switch is implemented as:
```java
SwitchCompat danmakuToggle = findViewById(R.id.danmaku_toggle);
danmakuToggle.setChecked(danmuController.isVisible()); // Reflect current state
danmakuToggle.setFocusable(true);
```

**And** the panel is D-pad navigable:
- Focus automatically on the toggle when panel opens
- D-pad Left/Right toggles the switch
- D-pad Center/OK also toggles the switch
- **Back** key closes the panel and returns focus to OSD

**And** the panel has a semi-transparent background overlay (0.7 alpha black) to maintain video visibility

**And** when opened, video playback continues in the background (no pause)

**And** the panel can be closed by:
- Pressing **Back** key
- Pressing the toggle (optional, based on UX preference)
- Auto-dismisses after 5 seconds of inactivity

### Story 3.3: Implement Real-Time Danmaku Toggle Logic

As a **user**,
I want the danmaku to immediately show or hide when I toggle the switch,
So that I see instant feedback without needing to restart playback (FR-10).

**Acceptance Criteria:**

**Given** the settings panel from Story 3.2 is open
**When** I toggle the "显示弹幕" switch to **ON**
**Then** `danmuController.show()` is called immediately

**And** danmaku appears on screen within 100ms
- Rendering resumes from current video position
- Danmaku are visible in the designated area (top 30%)

**When** I toggle the switch to **OFF**
**Then** `danmuController.hide()` is called immediately

**And** all danmaku disappear from screen within 100ms
- Canvas is cleared: `overlayView.renderDanmaku(Collections.emptyList())`
- Video playback continues uninterrupted

**And** the toggle state is persisted:
```java
SharedPreferences prefs = getSharedPreferences("danmaku_settings", MODE_PRIVATE);
prefs.edit().putBoolean("danmaku_enabled", isEnabled).apply();
```

**And** on next video playback, the persisted state is restored:
```java
boolean enabled = prefs.getBoolean("danmaku_enabled", true); // Default ON
if (enabled) {
    danmuController.show();
} else {
    danmuController.hide();
}
```

**And** the toggle operates smoothly:
- No lag between toggle action and visual response
- No video frame drops during toggle
- Audio playback unaffected

**And** end-to-end validation:
1. Play video with danmaku visible
2. Press D-pad Down → Navigate to "弹幕设置" → Press OK
3. Toggle switch to OFF → Danmaku disappears instantly
4. Toggle switch to ON → Danmaku reappears instantly
5. Close panel → Setting persists for next video

**And** edge case: If API hasn't loaded danmaku yet, toggle shows appropriate state (e.g., "加载中..." instead of hiding)

---

## Epic 4: Performance Optimization & Reliability

Even on low-end TV boxes, video playback remains smooth and never crashes due to danmaku rendering. The system monitors performance in real-time and automatically protects video quality by dropping danmaku packets when the device is under heavy load.

### Story 4.1: Implement Real-Time FPS Monitor

As a **developer**,
I want to implement a real-time FPS (frames per second) monitor for the danmaku rendering layer,
So that the system can detect performance degradation and trigger protective measures (FR-11).

**Acceptance Criteria:**

**Given** the `DanmakuOverlayView` from Epic 2
**When** I create `FpsMonitor.java` in `feature.danmaku.logic/`
**Then** the class tracks frame rendering timestamps using `Choreographer`:

```java
public class FpsMonitor {
    private static final int FRAME_WINDOW = 60; // Track last 60 frames
    private final long[] frameTimes = new long[FRAME_WINDOW];
    private int frameIndex = 0;
    
    public void markFrame() {
        frameTimes[frameIndex] = System.nanoTime();
        frameIndex = (frameIndex + 1) % FRAME_WINDOW;
    }
    
    public float getCurrentFps() {
        long earliest = frameTimes[frameIndex];
        long latest = frameTimes[(frameIndex - 1 + FRAME_WINDOW) % FRAME_WINDOW];
        long duration = latest - earliest;
        return (FRAME_WINDOW - 1) * 1_000_000_000f / duration;
    }
}
```

**And** in `DanmakuOverlayView.onDraw()`, mark each frame:
```java
@Override
protected void onDraw(Canvas canvas) {
    fpsMonitor.markFrame();
    // ... existing rendering logic
}
```

**And** `DanmuPresenter` polls FPS every 500ms:
```java
private void monitorPerformance() {
    handler.postDelayed(() -> {
        float currentFps = fpsMonitor.getCurrentFps();
        if (currentFps < 24.0f) {
            lowFpsCounter++;
        } else {
            lowFpsCounter = 0;
        }
        monitorPerformance();
    }, 500);
}
```

**And** FPS data is logged for debugging:
```java
Log.d(TAG, "Danmaku FPS: " + currentFps);
```

**And** the monitor has minimal performance overhead (<1ms per frame)

**And** unit tests verify FPS calculation accuracy with simulated frame intervals

### Story 4.2: Implement Circuit Breaker for Performance Protection

As a **system**,
I want to automatically reduce danmaku density when rendering performance drops below 24fps for 3 consecutive seconds,
So that video playback remains smooth even on low-end devices (FR-12, NFR-04).

**Acceptance Criteria:**

**Given** the FPS monitor from Story 4.1
**When** I add circuit breaker logic to `DanmuPresenter.java`
**Then** the class tracks low FPS duration:

```java
private static final int LOW_FPS_THRESHOLD_MS = 3000; // 3 seconds
private long lowFpsStartTime = 0;
private boolean circuitBreakerTriggered = false;

private void checkCircuitBreaker(float currentFps) {
    if (currentFps < 24.0f) {
        if (lowFpsStartTime == 0) {
            lowFpsStartTime = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lowFpsStartTime > LOW_FPS_THRESHOLD_MS) {
            triggerCircuitBreaker();
        }
    } else {
        resetCircuitBreaker();
    }
}
```

**And** when triggered, the system drops 60% of danmaku:
```java
private void triggerCircuitBreaker() {
    if (!circuitBreakerTriggered) {
        circuitBreakerTriggered = true;
        dropRate = 0.6f; // Drop 60% of incoming danmaku
        Log.w(TAG, "Circuit breaker triggered: dropping " + (dropRate * 100) + "% danmaku");
        scheduleRecovery();
    }
}
```

**And** in `DanmuRenderer.calculateVisibleDanmaku()`, apply drop rate:
```java
if (circuitBreakerActive && Math.random() < dropRate) {
    continue; // Skip this danmaku
}
```

**And** automatic recovery after 20 seconds (NFR-04):
```java
private void scheduleRecovery() {
    handler.postDelayed(() -> {
        circuitBreakerTriggered = false;
        dropRate = 0.0f;
        Log.i(TAG, "Circuit breaker recovered: resuming normal danmaku flow");
    }, 20_000); // 20 seconds
}
```

**And** if FPS returns to normal before 20 seconds, recovery happens immediately:
```java
private void resetCircuitBreaker() {
    lowFpsStartTime = 0;
    if (circuitBreakerTriggered && currentFps >= 24.0f) {
        // Immediate recovery if performance recovered
        handler.removeCallbacks(recoveryRunnable);
        circuitBreakerTriggered = false;
        dropRate = 0.0f;
    }
}
```

**And** end-to-end validation on low-end device (S905 chipset or similar):
1. Play video with 100+ danmaku/second
2. Monitor FPS drops below 24fps
3. After 3 seconds, verify danmaku density visibly reduces
4. Video playback remains smooth (no stuttering)
5. After 20 seconds, verify danmaku density returns to normal

### Story 4.3: Implement Object Pool for Memory Optimization

As a **developer**,
I want to implement an object pool for `DanmakuEntity` instances to limit memory usage to 15MB,
So that the app doesn't trigger OOM (Out of Memory) crashes on low-RAM devices (NFR-03).

**Acceptance Criteria:**

**Given** the `DanmakuEntity` model from Story 1.3
**When** I create `DanmakuEntityPool.java` in `feature.danmaku.view/`
**Then** the class implements a fixed-size pool:

```java
public class DanmakuEntityPool {
    private static final int MAX_POOL_SIZE = 5000; // ~15MB assuming 3KB per entity
    private final Queue<DanmakuEntity> pool = new LinkedList<>();
    private int activeCount = 0;
    
    public synchronized DanmakuEntity obtain() {
        DanmakuEntity entity = pool.poll();
        if (entity == null) {
            if (activeCount >= MAX_POOL_SIZE) {
                return null; // Pool exhausted, drop this danmaku
            }
            entity = new DanmakuEntity();
            activeCount++;
        }
        return entity;
    }
    
    public synchronized void recycle(DanmakuEntity entity) {
        entity.reset(); // Clear data to prevent memory leaks
        pool.offer(entity);
    }
}
```

**And** `DanmakuEntity` includes a `reset()` method:
```java
public void reset() {
    this.time = 0;
    this.text = null;
    this.mode = 0;
    this.color = null;
}
```

**And** `DanmuRepository` uses the pool when parsing API response:
```java
DanmakuEntity entity = pool.obtain();
if (entity == null) {
    Log.w(TAG, "Object pool exhausted, dropping danmaku");
    continue; // Skip this danmaku item
}
entity.time = jsonObject.get("time").getAsLong();
entity.text = jsonObject.get("text").getAsString();
// ... populate other fields
```

**And** `DanmuRenderer` recycles entities that scrolled off-screen:
```java
for (DanmakuEntity entity : offscreenList) {
    pool.recycle(entity);
}
```

**And** memory usage is profiled:
- Heap allocation stays below 15MB for danmaku feature
- No memory leaks detected after 30 minutes of playback
- GC pressure is minimal (no frequent pauses)

**And** unit tests verify pool behavior:
- Obtain from empty pool creates new instance
- Recycle and re-obtain reuses same instance
- Pool refuses to exceed MAX_POOL_SIZE

### Story 4.4: Implement Zero-Crash Policy and Complete Integration

As a **developer**,
I want to ensure the danmaku feature never crashes the app through defensive coding and input sanitization,
So that users have a reliable experience even when danmaku data is malformed (NFR-06, Zero-Crash Policy).

**Acceptance Criteria:**

**Given** all components from Epic 1-3
**When** I add error handling to critical paths
**Then** all `onDraw()` rendering is wrapped:

```java
@Override
protected void onDraw(Canvas canvas) {
    try {
        fpsMonitor.markFrame();
        // ... rendering logic
    } catch (Exception e) {
        Log.e(TAG, "Danmaku rendering error (non-fatal)", e);
        // Clear canvas to prevent visual artifacts
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }
}
```

**And** input sanitization is applied (NFR-06):
```java
public static String sanitizeDanmakuText(String input) {
    if (input == null) return "";
    return input
        .replace("\n", " ")      // Remove newlines
        .replace("\r", " ")      // Remove carriage returns
        .replace("\t", " ")      // Remove tabs
        .replaceAll("\\p{C}", "") // Remove all control characters
        .trim();
}
```

**And** sanitization is applied in `DanmuRepository` during parsing:
```java
entity.text = sanitizeDanmakuText(jsonObject.get("text").getAsString());
```

**And** API failures are handled gracefully (NFR-05):
```java
@Override
public void onError(Exception e) {
    Log.e(TAG, "Danmaku API failed", e);
    // Do NOT show error to user
    // UI shows "弹幕加载失败" silently in debug indicator only
}
```

**And** **legacy code deletion** (Architecture directive):
```java
// DELETE the following file completely:
// app/src/main/java/com/mynas/nastv/ui/LegacyDanmuManager.java
```

**And** final validation checklist:
- [ ] App launches without crashes
- [ ] Video playback works with danmaku enabled
- [ ] Video playback works with danmaku disabled
- [ ] Malformed danmaku text (with \n, \t, control chars) renders correctly
- [ ] Network timeout doesn't crash app
- [ ] Rapid seek operations don't cause crashes
- [ ] Memory usage stays under 15MB for danmaku objects
- [ ] Switch between videos doesn't leak memory
- [ ] Legacy `DanmuManager` is completely removed from codebase
- [ ] No references to old danmaku code remain (verified via search)

**And** comprehensive end-to-end test on target device:
1. Play video for 10 minutes with danmaku
2. Toggle danmaku on/off 20 times
3. Seek forward/backward 50 times
4. Switch between 5 different videos
5. Simulate network failures (airplane mode)
6. Inject malformed danmaku data (control characters, empty strings)
7. Monitor: Zero crashes, no visual glitches, smooth playback

**And** all acceptance criteria from all previous stories (1.1-4.3) are re-validated in final integration test

**And** performance benchmarks on low-end device:
- Cold start time: <500ms (NFR-01) ✓
- FPS with 100 items: >=24fps (NFR-02) ✓
- Memory usage: <15MB (NFR-03) ✓
- Recovery time: <20s (NFR-04) ✓




