# Capsule dynamique — Pixel 7 Pro
### Spécification technique + implémentation Kotlin / Jetpack Compose

---

## 1. Contraintes matérielles (Pixel 7 Pro)

| Élément | Valeur | Conséquence |
| --- | --- | --- |
| Barre d'état | 41 dp | le poinçon est *dans* cette bande ; une fenêtre overlay classique est masquée par SystemUI |
| Poinçon caméra | centré, ~10–30 dp du haut | la capsule doit s'aligner sur `DisplayCutout` ou, à défaut, démarrer à 6 dp |
| Bords incurvés | ~8 dp | marge latérale minimale de 8 dp pour les modes bords |
| Densité | 560 dpi (3,5×) | toutes les tailles en dp, jamais en px codés en dur |

Deux conséquences directes :

1. **Toucher au poinçon** : `TYPE_APPLICATION_OVERLAY` est *sous* la barre d'état ; ses
   touches y sont volées par SystemUI. Pour une capsule réellement interactive
   autour du poinçon, il faut `TYPE_ACCESSIBILITY_OVERLAY` (au-dessus de la barre),
   d'où le service d'accessibilité compagnon.
2. **Placement** : `setFitInsetsTypes(0)` + `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`,
   sinon l'origine `y` est le bas de la barre d'état et la capsule ne peut jamais
   entourer le poinçon.

---

## 2. Architecture

```
NotificationListenerService ─┐
MediaSessionManager ─────────┼──▶ ActivityEngine (StateFlow<IslandState>, max 3)
Capteurs internes ───────────┘            │
                                          ▼
                                OverlayHost (1 fenêtre / activité)
                                          │  ComposeView + LifecycleOwner
                                          ▼
                                   PillUi (Compact / Expanded / Carousel)
```

- **ActivityEngine** : pur Kotlin, testable, plafonné à 3 activités, focus + ordre.
- **OverlayHost** : une petite fenêtre par activité (une fenêtre pleine largeur
  bloquerait toutes les touches du haut de l'écran).
- **PillUi** : Compose, `Animatable` / `updateTransition`, Material You.

---

## 3. Fenêtres

```kotlin
private fun params(slot: Slot, wm: WindowManager, useA11y: Boolean): WindowManager.LayoutParams {
    val type = if (useA11y) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
               else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    return WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        gravity = Gravity.TOP or slot.gravity
        x = slot.x(wm)
        y = slot.y(wm)
        windowAnimations = 0
    }
}
```

```kotlin
enum class Edge { CENTER, LEFT, RIGHT }

class Slot(private val edge: Edge, private val marginDp: Int = 8) {
    val gravity: Int get() = when (edge) {
        Edge.CENTER -> Gravity.CENTER_HORIZONTAL
        Edge.LEFT -> Gravity.START
        Edge.RIGHT -> Gravity.END
    }

    fun x(wm: WindowManager): Int =
        if (edge == Edge.CENTER) 0 else dp(wm, marginDp)

    fun y(wm: WindowManager): Int {
        val insets = wm.currentWindowMetrics.windowInsets
        val top = insets.getInsetsIgnoringVisibility(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
        ).top
        // 40 dp de capsule : 6 dp de marge au-dessus du poinçon
        return maxOf(top - dp(wm, 34), dp(wm, 6))
    }
}
```

**Bords incurvés** : en mode `LEFT`/`RIGHT`, la capsule est décalée de 8 dp et son
rayon passe à 20 dp pour ne pas « mordre » la courbure de l'écran.

---

## 4. États de la capsule

```
Compact                    Expanded                     Carousel (3)
┌──────────────┐          ┌────────────────────┐       ┌──────────────┐
│ ● ▁▃▅ 0:42   │          │ (avatar) Titre     │       │ ● Rec  0:42  │
└──────────────┘          │ sous-titre · état  │       └──────────────┘
                          │ [ ] [ ] [ ]        │       ┌────────┐ ┌────────┐
                          └────────────────────┘       │⬆ 64 %  │ │➜ 200 m │
                                                       └────────┘ └────────┘
```

| État | Taille | Contenu |
| --- | --- | --- |
| Compact | 232 × 40 dp, rayon 20 | pastille d'état, waveform/icône, titre court, chrono |
| Expanded | 340 × 188 dp, rayon 42 | avatar, titre, sous-titre, contrôles 40 dp |
| Carousel 2/3 | 3 capsules simultanées | primaire au centre, secondaires en bulles sous/bords |

Règle de focus : la primaire occupe le centre (ou le bord imposé par le matériel),
les secondaires sont des bulles de 120 × 34 dp. Un tap sur une bulle **échange**
les places (swap) avec animation ressort.

---

## 5. Gestes Compose

```kotlin
@Composable
fun Capsule(task: Task, state: IslandState, engine: ActivityEngine) {
    val expanded = remember { Animatable(if (state.expanded) 1f else 0f) }

    val width by animateDpAsState(lerp(232.dp, 340.dp, expanded.value))
    val height by animateDpAsState(lerp(40.dp, 188.dp, expanded.value))
    val radius by animateDpAsState(lerp(20.dp, 42.dp, expanded.value))

    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(task.id) {
                detectTapGestures(
                    onTap = { /* ouvre l'activité en plein écran */ },
                    onLongPress = {
                        scope.launch {
                            expanded.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 380f))
                            engine.setExpanded(true)
                        }
                    }
                )
            }
            .pointerInput(task.id) {
                detectDragGestures(
                    onDragEnd = {
                        // swipe haut = réduire, gauche/droite = changer d'activité
                        if (dragY < -56.dp.toPx()) {
                            scope.launch { expanded.animateTo(0f, spring(0.8f, 400f)) }
                            engine.setExpanded(false)
                        } else if (abs(dragX) > 48.dp.toPx()) {
                            engine.rotate(if (dragX < 0) 1 else -1)
                        }
                    },
                    onDrag = { change, delta ->
                        dragX += delta.x; dragY += delta.y
                        change.consume()
                    }
                )
            }
    ) {
        Crossfade(targetState = expanded.value > 0.5f) { open ->
            if (open) ExpandedCard(task, engine) else CompactPill(task)
        }
    }
}
```

Points de production :

- `detectTapGestures` et `detectDragGestures` dans **deux** `pointerInput` distincts :
  le drag ne consomme qu'après le slop, le tap reste fiable.
- `Animatable` pour l'état, `animateDpAsState` pour les dimensions : une seule
  source de vérité pour la forme.
- `spring(dampingRatio = 0.78f, stiffness = 380f)` ≈ le ressort iOS.
- Le `LongPress` ne doit jamais être avalé par le drag : seuil de déplacement ≥ slop.

---

## 6. Moteur multi-activités

```kotlin
data class Task(
    val id: String,
    val kind: Kind,
    val title: String,
    val startedAt: Long = System.currentTimeMillis(),
    val progress: Float? = null,
    val levels: List<Float> = emptyList()
)

class ActivityEngine {
    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    fun upsert(t: Task) = _state.update { s ->
        val list = (s.tasks.filterNot { it.id == t.id } + t).takeLast(3)
        s.copy(tasks = list, focusId = s.focusId ?: t.id)
    }

    fun rotate(dir: Int) = _state.update { s ->
        if (s.tasks.size < 2) s else {
            val i = s.tasks.indexOfFirst { it.id == s.focusId }.coerceAtLeast(0)
            s.copy(focusId = s.tasks[(i + dir + s.tasks.size) % s.tasks.size].id)
        }
    }

    fun swap(id: String) = _state.update { s ->
        if (s.tasks.none { it.id == id }) s else s.copy(focusId = id)
    }
}
```

---

## 7. Material You

```kotlin
@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
```

La capsule reste **noire** (contraste avec le poinçon), mais les accents — waveform,
pastille d'état, barre de progression, focus — viennent de
`MaterialTheme.colorScheme.primary` (donc du fond d'écran sous Material You).

---

## 8. Règles d'interaction (récapitulatif)

| Geste | Effet |
| --- | --- |
| Tap | ouvre l'application de l'activité en plein écran |
| Appui long | déploie la carte de contrôle (pause, accept/refuse, étape suivante) |
| Swipe haut | réduit la carte et revient à la capsule |
| Swipe gauche/droite | fait défiler les 3 activités et change la priorité |
| Tap sur la bulle secondaire | échange les places (swap) avec l'activité principale |
| Drag (position déverrouillée) | repositionne verticalement |

---

## 9. État de l'APK (3.2)

Déjà implémenté : capsule overlay (accessibility overlay autour du poinçon),
compact/expanded, appui long = déploiement, swipe haut = réduction, swipe
horizontal = rotation des activités (Appel / Minuteur / Live), bulle secondaire
cliquable pour le swap, couleurs d'accent système, presets centre / bord gauche /
bord droit, verrou de position.

Reste spécifié mais non livré : rendu **simultané** de 3 fenêtres (le code actuel
cycle une seule capsule), et le thème Compose complet (l'APK est en Views).
