# AOSP code in this fork

Code ported from AOSP's `platform/frameworks/base` and `platform/frameworks/native` stays under
AOSP's Apache License 2.0 (`LICENSE-APACHE-2.0.txt`), next to ATL's GPLv3 (`LICENSE.txt`). This
file lists what this fork ports and where it came from, for the notices a distribution must carry;
`NOTICE-AOSP.txt` is the attribution notice of frameworks/base's NOTICE file that the license's
section 4(d) asks a distribution to carry.

- A whole-file copy keeps AOSP's header, which also names the AOSP file and tag and says it is
  modified.
- A file with ported parts starts with a "Portions Copyright (C) <year> The Android Open Source
  Project" notice that names the AOSP file and tag; the ported blocks are marked
  `/* Copyright (C) <year> The Android Open Source Project */`.
- Upstream ATL's own AOSP copies carry AOSP's header: `grep -rl "The Android Open Source Project"
  src/api-impl` finds them.

The fork's ports come from the tag `android-security-10.0.0_r75`, the one dine-art's ART is on.
Paths are relative to `src/api-impl/android/` here and to `frameworks/base` in AOSP unless they
name another project.

| File | AOSP file | Ported |
|---|---|---|
| `view/FocusFinder.java` | `core/java/android/view/FocusFinder.java` | the whole file; `@TestApi` commented out, `getContext()` for `mContext`, reformatted |
| `view/View.java` | `core/java/android/view/View.java` | touch: `dispatchTouchEvent`, `onTouchEvent`, `isInScrollingContainer`, `pointInView`, `checkForLongClick`, `cancelLongPress`, the long press, tap, click and unpress runnables, `performLongClick`, `setPressed`, the callbacks a detach removes; focus: `requestFocus(int, Rect)` and the checks it makes, `handleFocusGainInternal`, `clearFocus`, `clearFocusInternal`, `unFocus`, `addFocusables`, `addTouchables`, `addKeyboardNavigationClusters`, `findUserSetNextFocus`, `setFocusableInTouchMode`, `onFocusChanged`'s unpress on a focus loss and dispatcher reset; keys: `dispatchKeyEvent`, `onKeyDown`, `onKeyUp`; `requestRectangleOnScreen`, `mergeDrawableStates` |
| `view/ViewGroup.java` | `core/java/android/view/ViewGroup.java` | `dispatchTouchEvent` with its touch targets and helpers, `requestDisallowInterceptTouchEvent`, `dispatchSetPressed`, `dispatchKeyEvent`; focus: `requestFocus`, `onRequestFocusInDescendants`, `requestChildFocus`, `clearChildFocus`, `clearFocus`, `unFocus`, `hasFocus`, `findFocus`, `addFocusables`, `addKeyboardNavigationClusters`, `setDescendantFocusability` |
| `view/ATLInput.java` | `core/java/android/view/ViewRootImpl.java` | touch mode (`enterTouchMode`, `leaveTouchMode`, `findAncestorToTakeFocusInTouchMode`, `checkForLeavingTouchModeAndConsume`, `isNavigationKey`, `isTypingKey`) and `performFocusNavigation` |
| `view/MotionEvent.java` | `core/java/android/view/MotionEvent.java` | an AOSP copy; `split` written over ATL's pointer arrays after AOSP's |
| `view/MotionEvent.java` | `frameworks/native`: `libs/input/Input.cpp` | `isTouchEvent`, after `MotionEvent::isTouchEvent` |
| `view/Window.java` | `core/java/com/android/internal/policy/PhoneWindow.java` | `superDispatchKeyEvent`, `superDispatchTouchEvent`, `getCurrentFocus` |
| `app/Activity.java` | `core/java/android/app/Activity.java` | `dispatchKeyEvent`, `onKeyDown` and `onKeyUp` (Back), `dispatchTouchEvent` |
| `app/Dialog.java` | `core/java/android/app/Dialog.java` | `dispatchTouchEvent`, `dispatchKeyEvent`, `onKeyDown`, `onKeyUp`, `onBackPressed` |
| `widget/ScrollView.java` | `core/java/android/widget/ScrollView.java` | `scrollTo` and `clamp`, scrolling a focused child or a rectangle into view (`computeScrollDeltaToGetChildRectOnScreen`) |
| `widget/HorizontalScrollView.java` | `core/java/android/widget/HorizontalScrollView.java` | the same across |
| `graphics/drawable/DrawableContainer.java` | `graphics/java/android/graphics/drawable/DrawableContainer.java` | `getCurrent` |

Open: Dine's app bundle carries `api-impl.jar` without these notices, `NOTICE-AOSP.txt` or the
Apache text.
