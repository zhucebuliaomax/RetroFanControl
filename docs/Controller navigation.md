# Controller Navigation

## Purpose

This document defines the minimum controller-navigation behavior for the Compose user interface. It is an implementation guide for a later feature and does not require controller-specific business logic or a custom visual system.

The intended scope is limited to simple focus navigation and activation. Existing touch behavior must remain unchanged.

## Input behavior

- The directional pad and left stick move focus through supported controls. Vertical screen content uses up and down as its primary navigation direction.
- The controller confirm button, such as the A button, performs the focused control's existing click action.
- The controller back button, such as the B button, follows the existing Android back behavior. On a dialog it dismisses the dialog when dismissal is already allowed; on the main screen it leaves the screen.
- When focus moves to an item outside the visible viewport, the main page or dialog scrolls enough to keep that item visible.
- Initial focus on the main screen goes to the first actual settings item rather than the top app-bar back button.
- The top app bar has no back button. Android back behavior remains available through the controller back button and the system back action.

Do not add controller-only actions, shoulder-button shortcuts, long-press adjustment, stick dead-zone handling, or a separate controller mode.

## Required Android-native implementation pattern

All current and future controller-navigation changes must use the standard Android and Jetpack Compose focus system. This is the project-wide implementation rule, not an optional recommendation.

- Use Material focus semantics and Compose APIs such as `focusable`, `focusGroup`, `FocusRequester`, and `focusProperties` for UI navigation.
- Use `BringIntoViewRequester` for focus-triggered scrolling and provide a page-level `BringIntoViewSpec` through `LocalBringIntoViewSpec` when the focused item needs a consistent landing position.
- Keep an `80.dp` safe margin between a focused item and the leading or trailing edge of its scroll viewport. A focus relocation request must scroll only when the focused bounds enter this margin.
- Apply the `PaddingValues` supplied by `Scaffold` to the scroll container's root before applying `verticalScroll`. This constrains the viewport below the app bar instead of treating the app-bar inset as content that can scroll away.
- Do not add real layout padding to every focusable item to simulate a focus safe area. Focus scrolling must not change card sizes, section spacing, or touch layout.
- Prefer the focus system over activity-level `KeyEvent` or `MotionEvent` interception. Add low-level controller input handling only when a documented device compatibility issue cannot be represented by Compose focus APIs.
- Preserve standard Android Back behavior and Material's default focus indication.

For a settings-style application, this pattern is preferred over game-specific controller libraries. The Android Game Controller APIs are reserved for raw axes, triggers, advanced buttons, or compatibility cases that the standard focus system does not handle.

## Focus appearance

Use the default focus indication supplied by Compose and Material components. Do not introduce custom borders, background colors, scaling, animation, or other controller-specific focus visuals.

Where an existing custom or compound component does not participate correctly in focus navigation, make the minimum focus-related change needed while retaining the platform's default indication.

## Main-screen focus model

Each supported settings row is one focus target. Focus follows the visual order of the screen:

1. Fan-curve selection.
2. Edit curve, when an active curve makes the row visible.
3. Floating-overlay row.
4. The complete telemetry panel.
5. Authorization-management rows in their displayed order.
6. Other simple clickable main-screen controls, including the GitHub link.

The first fan-curve row is the top of the focus sequence. Pressing up from it must not move focus to an app-bar control.

### Compound rows and switches

A settings row containing a switch is a single focus target:

- The row receives focus.
- Its trailing `Switch` does not create an additional stop in the focus sequence.
- Pressing the confirm button on the row performs the existing toggle action.
- Touch interaction with the row and switch continues to work as before.

This rule applies to the floating-overlay row, automatic-start row, and any future simple setting that combines a row action with a trailing switch.

### Live telemetry panel

The second item in the Live Telemetry section is one focus target covering the complete panel, including the CPU, GPU, memory, and battery content.

- Pressing up moves focus to the floating-overlay row.
- Pressing down moves focus to the first Authorization Management row.
- Pressing the confirm button while the panel is focused does nothing.
- CPU, GPU, memory, battery, and all content inside this panel are excluded from controller focus navigation.
- Existing touch actions inside the panel, including the CPU and GPU detail actions, remain unchanged.

The panel is therefore a deliberate non-activating waypoint in the controller focus sequence, not a group with internal navigation.

## Fan-curve selection dialog

The fan-curve selection dialog is included in controller support:

- Up and down move focus through Off, the available curve choices, Add fan curve, and Cancel in their visual order.
- The confirm button performs the focused item's existing action.
- The back button dismisses the dialog through its existing dismiss behavior.
- Opening the dialog places focus on a useful dialog item, preferably the currently selected curve when it is available.
- Focus remains within the dialog while it is open.
- Closing the dialog restores focus to the main-screen control that opened it.
- The choice list scrolls automatically when the focused item is outside its visible area.

Use one focus target for each complete choice row. The radio icon inside a choice is not a separate focus target.

## Explicit exclusions

The following work is outside this feature:

- Controller navigation or value adjustment inside the fan-curve editor dialog.
- Controller control of curve points, sliders, rename, delete, reset, import, or export actions in the curve editor.
- Internal navigation within the Live Telemetry panel.
- A controller action for the focused Live Telemetry panel.
- Custom focus visuals.
- Remapping controllers or supporting controller-specific layouts.
- Changing fan-control state, persistence, telemetry, or hardware behavior.

Opening the curve editor through a supported main-screen row does not imply controller support after the editor opens. Its existing touch interface remains unchanged.

## Implementation boundaries

- Keep reusable focus behavior for settings rows in `core/designsystem` when it applies to all such rows.
- Keep feature-specific focus grouping for fan and telemetry content in `feature/fan`.
- Keep screen ordering, scrolling, dialog focus containment, and focus restoration in `app/ui`.
- Prefer Compose focus APIs and the existing Material semantics over activity-level raw key interception.
- Avoid consuming confirm or directional events globally when the standard Compose focus system can handle them.
- Preserve accessibility semantics and ensure that disabling a nested controller focus target does not remove its required touch or accessibility behavior.
- Dynamic content must keep a valid focus path. In particular, showing or hiding Edit curve must not leave focus pointing to a removed node.

## Acceptance criteria

The future implementation is complete when all of the following are true:

- A controller can enter the main settings sequence, move through every supported item, and activate ordinary rows without using touch.
- Initial focus does not remain trapped on the app-bar back button.
- Moving through a long page scrolls focused controls into view.
- Focused controls remain at least `80.dp` from the top and bottom edges of their scroll viewport whenever the content has enough scroll range.
- Focused controls are never placed underneath the app bar; `Scaffold` content padding constrains the scroll viewport.
- Rows containing switches create only one controller focus stop and toggle when confirmed.
- The complete Live Telemetry panel creates exactly one focus stop, has no confirm action, and moves directly between the overlay row and the first authorization row.
- No child of the Live Telemetry panel receives controller focus.
- The fan-curve selection dialog is fully usable with up, down, confirm, and back, and focus returns to its opener afterward.
- The fan-curve editor receives no new controller behavior.
- Focus appearance remains the default Compose/Material appearance.
- Existing touch interactions continue to behave as before.
