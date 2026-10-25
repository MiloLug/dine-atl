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
| `view/View.java` | `core/java/android/view/View.java` | `drawBackground` without the display list; touch: `dispatchTouchEvent`, `onTouchEvent`, `isInScrollingContainer`, `pointInView`, `checkForLongClick`, `cancelLongPress`, the long press, tap, click and unpress runnables, `performLongClick`, `setPressed`, the callbacks a detach removes; focus: `requestFocus(int, Rect)` and the checks it makes, `handleFocusGainInternal`, `clearFocus`, `clearFocusInternal`, `unFocus`, `addFocusables`, `addTouchables`, `addKeyboardNavigationClusters`, `findUserSetNextFocus`, `setFocusableInTouchMode`, `onFocusChanged`'s unpress on a focus loss and dispatcher reset; keys: `dispatchKeyEvent`, `onKeyDown`, `onKeyUp`; `requestRectangleOnScreen`, `mergeDrawableStates` |
| `view/ViewGroup.java` | `core/java/android/view/ViewGroup.java` | `dispatchTouchEvent` with its touch targets and helpers, `requestDisallowInterceptTouchEvent`, `dispatchSetPressed`, `dispatchKeyEvent`; focus: `requestFocus`, `onRequestFocusInDescendants`, `requestChildFocus`, `clearChildFocus`, `clearFocus`, `unFocus`, `hasFocus`, `findFocus`, `addFocusables`, `addKeyboardNavigationClusters`, `setDescendantFocusability` |
| `view/ATLInput.java` | `core/java/android/view/ViewRootImpl.java` | touch mode (`enterTouchMode`, `leaveTouchMode`, `findAncestorToTakeFocusInTouchMode`, `checkForLeavingTouchModeAndConsume`, `isNavigationKey`, `isTypingKey`) and `performFocusNavigation` |
| `view/MotionEvent.java` | `core/java/android/view/MotionEvent.java` | an AOSP copy; `split` written over ATL's pointer arrays after AOSP's |
| `view/MotionEvent.java` | `frameworks/native`: `libs/input/Input.cpp` | `isTouchEvent`, after `MotionEvent::isTouchEvent` |
| `view/Window.java` | `core/java/com/android/internal/policy/PhoneWindow.java` | `superDispatchKeyEvent`, `superDispatchTouchEvent`, `getCurrentFocus` |
| `app/Activity.java` | `core/java/android/app/Activity.java` | `dispatchKeyEvent`, `onKeyDown` and `onKeyUp` (Back), `dispatchTouchEvent` |
| `app/Dialog.java` | `core/java/android/app/Dialog.java` | `dispatchTouchEvent`, `dispatchKeyEvent`, `onKeyDown`, `onKeyUp`, `onBackPressed` |
| `widget/ScrollView.java` | `core/java/android/widget/ScrollView.java` | `scrollTo` and `clamp`, scrolling a focused child or a rectangle into view (`computeScrollDeltaToGetChildRectOnScreen`) |
| `widget/HorizontalScrollView.java` | `core/java/android/widget/HorizontalScrollView.java` | the same across |
| `graphics/drawable/DrawableContainer.java` | `graphics/java/android/graphics/drawable/DrawableContainer.java` | `getCurrent`, `selectDrawable` without fades, `initializeDrawableForDisplay` (visibility, state, bounds, layout direction), `setConstantState`, `getConstantState`, `mutate`, `cloneConstantState`, the `Drawable.Callback` forwarding, `onBoundsChange`; `DrawableContainerState`'s copy constructor with its child futures, `getChild`, `getChildren`, `addChild`, `createAllFutures`, `prepareDrawable`, `mutate`, `canConstantState` |
| `graphics/drawable/StateListDrawable.java` | `graphics/java/android/graphics/drawable/StateListDrawable.java` | `cloneConstantState`, `StateListState.newDrawable` (upstream ATL's copy has the rest) |
| `graphics/drawable/ColorDrawable.java` | `graphics/java/android/graphics/drawable/ColorDrawable.java` | `draw`, `ColorState` for the color alone, `mutate`, `setColor`'s invalidate |
| `graphics/drawable/LayerDrawable.java` | `graphics/java/android/graphics/drawable/LayerDrawable.java` | `ChildDrawable(orig, owner, res)`'s share of a child without a constant state, in `LayerState`'s copy constructor |
| `widget/ImageView.java` | `core/java/android/widget/ImageView.java` | `configureBounds`, `onDraw` (as `atlDrawContent`), `drawableStateChanged` |
| `widget/CompoundButton.java` | `core/java/android/widget/CompoundButton.java` | `toggle`, `performClick`, `setChecked`, `onCreateDrawableState` |
| `widget/RadioButton.java` | `core/java/android/widget/RadioButton.java` | `toggle` |
| `text/Editable.java` | `core/java/android/text/Editable.java` | the whole file |
| `text/InputType.java` | `core/java/android/text/InputType.java` | the whole file |
| `text/InputFilter.java` | `core/java/android/text/InputFilter.java` | the whole file; `AllCaps` upper-cases with `String.toUpperCase` (no ICU `CaseMap`) and keeps spans only when the length stays |
| `view/inputmethod/EditorInfo.java` | `core/java/android/view/inputmethod/EditorInfo.java` | the `IME_*` constants, `implements InputType` |
| `widget/TextView.java` | `core/java/android/widget/TextView.java` | `onEditorAction` (its Enter keys go through `ATLInput.dispatchKeyFromIme`), `getSelectionStart`, `getSelectionEnd`, `hasSelection` |
| `widget/EditText.java` | `core/java/android/widget/EditText.java` | `getText`, `setSelection`, `selectAll`, `extendSelection` |
| `widget/EditText.java` | `core/java/android/widget/TextView.java` | `setText`'s filters, Editable and watcher calls, the ChangeWatcher's TextWatcher part, `sendBeforeTextChanged`, `sendOnTextChanged`, `sendAfterTextChanged`, `isMultilineInputType`, `isPasswordInputType`, `setInputTypeSingleLine`, the constructor's `inputType`, `singleLine`, `password` and `maxLength` handling, `shouldAdvanceFocusOnEnter`, `onKeyDown` and `onKeyUp`'s Enter |

Open: Dine's app bundle carries `api-impl.jar` without these notices, `NOTICE-AOSP.txt` or the
Apache text.
