# Floating pill engine — Android architecture (Kotlin + Compose)

Covers: continuous live activities (recorder / transfer / navigation), up to three
concurrent activities, edge-anchored pill (punch-hole / secondary display),
availability over home + third-party apps, and tap / long-press / swipe.

---

## 1. Layers

```
 notifications ──▶ LiveActivitySource ──▶ ActivityEngine (StateFlow<IslandState>)
 media sessions ──┘                              │
                                                 ▼
                                    OverlayHost (one window per activity)
                                                 │  ComposeView + lifecycle owners
                                                 ▼
                                    PillUi (compact / expanded / stack)
```

- **LiveActivitySource**: `NotificationListenerService` + `MediaSessionManager`.
  Maps notifications to activities: `setProgress` → transfer, `setChronometer` →
  recorder/timer, `category == NAVIGATION` + ongoing → navigation.
- **ActivityEngine**: pure Kotlin state holder. Owns up to 3 `Activity` models,
  their priority and focus. No Android UI dependencies → testable.
- **OverlayHost**: owns `WindowManager` windows and the Compose tree.
- **PillUi**: presentation + gestures.

---

## 2. Window strategy

| Approach | Pros | Cons |
| --- | --- | --- |
| One full-width window, pills drawn inside | one touch surface, easy animation | blocks touches across the top edge |
| **One small window per activity** (chosen) | only the pill blocks touches; independent z-order | N `addView` calls, must keep them in sync |

`WindowManager.LayoutParams` essentials:

```kotlin
private fun paramsFor(slot: Slot, metrics: WindowMetrics): WindowManager.LayoutParams {
    val type = if (a11yAvailable) {
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY   // above the status bar
    } else {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY     // below it
    }

    val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    return WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        type, flags, PixelFormat.TRANSLUCENT
    ).apply {
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        gravity = Gravity.TOP or slot.horizontalGravity
        x = slot.x(metrics)
        y = slot.y(metrics)
        windowAnimations = 0
    }
}
```

### Edge anchoring (right-side punch-hole / secondary display)

```kotlin
enum class Edge { CENTER, LEFT, RIGHT }

class Slot(
    val index: Int,          // 0..2
    val edge: Edge,
    val offsetDp: Int
) {
    fun horizontalGravity() = when (edge) {
        Edge.CENTER -> Gravity.CENTER_HORIZONTAL
        Edge.LEFT -> Gravity.START
        Edge.RIGHT -> Gravity.END
    }

    fun x(m: WindowMetrics): Int = when (edge) {
        Edge.CENTER -> 0
        Edge.LEFT -> dp(m, offsetDp)
        Edge.RIGHT -> dp(m, offsetDp)
    }

    fun y(m: WindowMetrics): Int {
        val cut = m.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.displayCutout() or WindowInsets.Type.statusBars()
        ).top
        return when (edge) {
            Edge.CENTER -> maxOf(cut - dp(m, 34), dp(m, 4))   // around the hole
            Edge.LEFT, Edge.RIGHT -> maxOf(cut - dp(m, 34), dp(m, 4))
        }
    }
}
```

**Three concurrent activities** = three slots: primary at centre (or the edge the
hardware dictates), second and third tucked to the left/right edge, stacked
vertically when the bar is crowded:

```
                ┌──────────────┐
                │  ●  Rec 0:42 │   slot 0  (centre / camera)
                └──────────────┘
   ┌───────────┐              ┌───────────┐
   │ ⬆ 64%     │              │ ➜ 200 m   │   slots 1 / 2 (edges)
   └───────────┘              └───────────┘
```

Swiping the primary pill horizontally promotes a neighbour into slot 0 and demotes
the old primary to the freed edge slot — a `swap` in the engine, animated with
`animateLayoutChanges`-style springs.

---

## 3. Engine

```kotlin
sealed interface Kind { object Call; object Recorder; object Transfer; object Navigation }

data class LiveTask(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val progress: Float? = null,
    val level: List<Float> = emptyList(),     // recorder waveform buckets
    val notificationKey: String? = null
)

data class IslandState(
    val tasks: List<LiveTask> = emptyList(),  // max 3
    val focusId: String? = null,
    val expanded: Boolean = false
)

class ActivityEngine {
    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    fun upsert(task: LiveTask) = _state.update { s ->
        val next = (s.tasks.filterNot { it.id == task.id } + task).takeLast(3)
        s.copy(tasks = next, focusId = s.focusId ?: task.id)
    }

    fun remove(id: String) = _state.update { s ->
        val next = s.tasks.filterNot { it.id == id }
        s.copy(tasks = next, focusId = s.focusId.takeUnless { it == id } ?: next.firstOrNull()?.id)
    }

    fun rotate(direction: Int) = _state.update { s ->
        if (s.tasks.size < 2) s else {
            val i = s.tasks.indexOfFirst { it.id == s.focusId }.coerceAtLeast(0)
            val n = (i + direction + s.tasks.size) % s.tasks.size
            s.copy(focusId = s.tasks[n].id)
        }
    }
}
```

---

## 4. Overlay service

An overlay needs a `ComposeView` with lifecycle/saved-state owners or it crashes:

```kotlin
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private lateinit var windowManager: WindowManager
    private val hosts = mutableMapOf<String, ComposeView>()

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        windowManager = getSystemService(WindowManager::class.java)
        startForeground(1, buildNotification(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override val lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedState.savedStateRegistry

    private fun attach(task: LiveTask, slot: Slot) {
        if (hosts.containsKey(task.id)) return
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent { PillUi(task, engine.state.collectAsState()) }
        }
        val lp = paramsFor(slot, windowManager.currentWindowMetrics)
        windowManager.addView(view, lp)
        hosts[task.id] = view
    }

    private fun detach(id: String) {
        hosts.remove(id)?.let { runCatching { windowManager.removeView(it) } }
    }
}
```

Foreground service type `specialUse` needs the manifest permission and the
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property (Android 14+).

---

## 5. Pill UI + gestures (Compose)

```kotlin
@Composable
fun PillUi(task: LiveTask, state: IslandState, onTap: () -> Unit) {
    val expanded by animateFloatAsState(
        if (state.expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "expand"
    )

    Box(
        Modifier
            .width(lerp(232.dp, 340.dp, expanded))
            .height(lerp(40.dp, 188.dp, expanded))
            .clip(RoundedCornerShape(lerp(20.dp, 42.dp, expanded)))
            .background(Color.Black)
            .pointerInput(task.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onExpand() }        // interactive card
                )
            }
            .pointerInput(task.id) {
                detectHorizontalDragGestures { _, dx ->
                    if (abs(dx) > 48.dp.toPx()) onSwipe(dx > 0)
                }
            }
    ) {
        if (expanded > 0.5f) ExpandedCard(task) else CompactPill(task)
    }
}
```

> Note: with `detectTapGestures` + `detectHorizontalDragGestures` on the same
> `pointerInput`, the drag consumes after slop; taps stay taps. Keep the tap
> decision threshold-based (like the APK's `ViewConfiguration` slop fix) or quick
> taps feel dead.

---

## 6. Notification → activity mapping

```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification) {
    val n = sbn.notification ?: return
    val e = n.extras
    val key = sbn.key

    when {
        e.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ->
            engine.upsert(LiveTask(
                id = key, kind = Kind.Transfer,
                title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Transfer",
                progress = e.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() /
                        e.getInt(Notification.EXTRA_PROGRESS_MAX, 100)
            ))

        e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) ->
            engine.upsert(LiveTask(
                id = key, kind = Kind.Recorder,
                title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Recording",
                startedAt = n.`when`
            ))

        n.category == Notification.CATEGORY_NAVIGATION ->
            engine.upsert(LiveTask(id = key, kind = Kind.Navigation, title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Navigation"))
    }
}

override fun onNotificationRemoved(sbn: StatusBarNotification) {
    engine.remove(sbn.key)
}
```

Recorder waveform: the recorder app reports levels over a `LocalBroadcast`/
`ContentProvider`; bucket to five floats at 6 Hz and write them into the task.
Do not animate inside a notification — push state.

---

## 7. Availability & lifecycle

- `SYSTEM_ALERT_WINDOW` (or the accessibility overlay) keeps the pill above
  third-party apps and the launcher while unlocked.
- A foreground service with `specialUse` keeps the process alive; `BOOT_COMPLETED`
  restarts it; battery-optimisation exemption is optional.
- On lock screen: either hide (`KeyguardManager.isKeyguardLocked`) or render a
  reduced version; overlays above the keyguard need `FLAG_SHOW_WHEN_LOCKED`-style
  system permissions, so hiding is the practical default.
- Cutout / orientation changes: recompute `Slot.x/y` from
  `WindowManager.currentWindowMetrics` on `onConfigurationChanged`.

---

## 8. Gesture contract

| Gesture | Result |
| --- | --- |
| Tap | open the owning app (`PendingIntent` / deep link) |
| Long-press | expand the interactive card in place |
| Swipe ←/→ | promote the next activity into the primary slot |
| Swipe ↑ | collapse |
| Drag (unlocked) | reposition vertically |

Our shipped APK (3.1) implements tap / long-press / swipe cycling / edge
anchoring. Rendering **three windows simultaneously** is specified above but not
yet in the APK: it needs the multi-window `OverlayHost` refactor.
