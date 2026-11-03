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
| `content/ActivityNotFoundException.java` | `core/java/android/content/ActivityNotFoundException.java` | the whole file |
| `view/FocusFinder.java` | `core/java/android/view/FocusFinder.java` | the whole file; `@TestApi` commented out, `getContext()` for `mContext`, reformatted |
| `view/View.java` | `core/java/android/view/View.java` | `drawBackground` without the display list; touch: `dispatchTouchEvent`, `onTouchEvent`, `isInScrollingContainer`, `pointInView`, `checkForLongClick`, `cancelLongPress`, the long press, tap, click and unpress runnables, `performLongClick`, `setPressed`, the callbacks a detach removes; focus: `requestFocus(int, Rect)` and the checks it makes, `handleFocusGainInternal`, `clearFocus`, `clearFocusInternal`, `unFocus`, `addFocusables`, `addTouchables`, `addKeyboardNavigationClusters`, `findUserSetNextFocus`, `setFocusableInTouchMode`, `onFocusChanged`'s unpress on a focus loss and dispatcher reset; keys: `dispatchKeyEvent`, `onKeyDown`, `onKeyUp`; `requestRectangleOnScreen`, `mergeDrawableStates`; `layout`'s layout-required flag (`PFLAG_LAYOUT_REQUIRED` set by `measure`), `offsetTopAndBottom` and `offsetLeftAndRight` as moves that invalidate |
| `view/ViewGroup.java` | `core/java/android/view/ViewGroup.java` | `dispatchTouchEvent` with its touch targets and helpers, `requestDisallowInterceptTouchEvent`, `dispatchSetPressed`, `dispatchKeyEvent`; focus: `requestFocus`, `onRequestFocusInDescendants`, `requestChildFocus`, `clearChildFocus`, `clearFocus`, `unFocus`, `hasFocus`, `findFocus`, `addFocusables`, `addKeyboardNavigationClusters`, `setDescendantFocusability`; `offsetChildrenTopAndBottom` |
| `view/ATLInput.java` | `core/java/android/view/ViewRootImpl.java` | touch mode (`enterTouchMode`, `leaveTouchMode`, `findAncestorToTakeFocusInTouchMode`, `checkForLeavingTouchModeAndConsume`, `isNavigationKey`, `isTypingKey`) and `performFocusNavigation` |
| `view/MotionEvent.java` | `core/java/android/view/MotionEvent.java` | an AOSP copy; `split` written over ATL's pointer arrays after AOSP's |
| `view/MotionEvent.java` | `frameworks/native`: `libs/input/Input.cpp` | `isTouchEvent`, after `MotionEvent::isTouchEvent` |
| `view/Window.java` | `core/java/com/android/internal/policy/PhoneWindow.java` | `superDispatchKeyEvent`, `superDispatchTouchEvent`, `getCurrentFocus` |
| `app/Activity.java` | `core/java/android/app/Activity.java` | `dispatchKeyEvent`, `onKeyDown` and `onKeyUp` (Back), `dispatchTouchEvent`; `requestPermissions` with its checks and its result (answered from `checkSelfPermission`, no permission activity); the activity lifecycle callbacks: `registerActivityLifecycleCallbacks`, the application's and the activity's callbacks called before, in and after each lifecycle method, in AOSP's order |
| `app/Application.java` | `core/java/android/app/Application.java` | `ActivityLifecycleCallbacks` with API 29's pre and post methods, `registerActivityLifecycleCallbacks`, `unregisterActivityLifecycleCallbacks`, `collectActivityLifecycleCallbacks` |
| `app/ActionBar.java` | `core/java/android/app/ActionBar.java` | the API: constants, methods and their defaults, `Tab`, `TabListener`, `LayoutParams`, the listener interfaces; no documentation |
| `app/ATLActionBar.java` | `core/java/com/android/internal/app/WindowDecorActionBar.java` | what tab and list navigation do: `addTab` selecting the first tab, `selectTab` and its listener calls, `removeTabAt`, `setNavigationMode` keeping the selected tab, `setSelectedNavigationItem`, `getSelectedNavigationIndex`, `getNavigationItemCount`, `setDisplayOptions` with a mask |
| `graphics/Bitmap.java` | `graphics/java/android/graphics/Bitmap.java` | `checkRecycled`, `checkPixelAccess`, `checkPixelsAccess`; the checks of `getPixels`, `setPixels`, `getPixel`, `setPixel`, `eraseColor`, `copy` and `copyPixelsToBuffer`'s buffer size; `createBitmap(Bitmap)` returning an immutable source itself |
| `graphics/Canvas.java` | `graphics/java/android/graphics/Canvas.java` | the three `drawPicture` |
| `graphics/drawable/PictureDrawable.java` | `graphics/java/android/graphics/drawable/PictureDrawable.java` | `draw`, the intrinsic size, `getPicture`, `setPicture`, `getOpacity` |
| `graphics/drawable/ScaleDrawable.java` | `graphics/java/android/graphics/drawable/ScaleDrawable.java` | the whole file without documentation, theme attributes and `getOpacity` |
| `graphics/drawable/InsetDrawable.java` | `graphics/java/android/graphics/drawable/InsetDrawable.java` | the whole file without documentation, theme attributes, optical insets and density changes |
| `graphics/drawable/RotateDrawable.java` | `graphics/java/android/graphics/drawable/RotateDrawable.java` | the whole file without documentation and theme attributes |
| `graphics/drawable/AnimatedRotateDrawable.java` | `graphics/java/android/graphics/drawable/AnimatedRotateDrawable.java` | the whole file without documentation and theme attributes |
| `graphics/drawable/Drawable.java` | `graphics/java/android/graphics/drawable/Drawable.java` | `setLevel`, `getLevel`, `onLevelChange`, `getAlpha` |
| `graphics/drawable/ColorDrawable.java` | `graphics/java/android/graphics/drawable/ColorDrawable.java` | `inflate`'s color |
| `graphics/drawable/AdaptiveIconDrawable.java` | `graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java` | an AOSP copy; `draw` as AOSP's again (the layers through a BitmapShader into the mask) |
| `app/Dialog.java` | `core/java/android/app/Dialog.java` | `dispatchTouchEvent`, `dispatchKeyEvent`, `onKeyDown`, `onKeyUp`, `onBackPressed` |
| `widget/ScrollView.java` | `core/java/android/widget/ScrollView.java` | `scrollTo` and `clamp`, scrolling a focused child or a rectangle into view (`computeScrollDeltaToGetChildRectOnScreen`) |
| `widget/HorizontalScrollView.java` | `core/java/android/widget/HorizontalScrollView.java` | the same across |
| `graphics/drawable/DrawableContainer.java` | `graphics/java/android/graphics/drawable/DrawableContainer.java` | `getCurrent`, `selectDrawable` without fades, `initializeDrawableForDisplay` (visibility, state, bounds, layout direction), `setConstantState`, `getConstantState`, `mutate`, `cloneConstantState`, the `Drawable.Callback` forwarding, `onBoundsChange`; `DrawableContainerState`'s copy constructor with its child futures, `getChild`, `getChildren`, `addChild`, `createAllFutures`, `prepareDrawable`, `mutate`, `canConstantState` |
| `graphics/drawable/StateListDrawable.java` | `graphics/java/android/graphics/drawable/StateListDrawable.java` | `cloneConstantState`, `StateListState.newDrawable` (upstream ATL's copy has the rest) |
| `graphics/drawable/ColorDrawable.java` | `graphics/java/android/graphics/drawable/ColorDrawable.java` | `draw`, `ColorState` for the color alone, `mutate`, `setColor`'s invalidate |
| `graphics/drawable/LayerDrawable.java` | `graphics/java/android/graphics/drawable/LayerDrawable.java` | `ChildDrawable(orig, owner, res)`'s share of a child without a constant state, in `LayerState`'s copy constructor |
| `widget/ImageView.java` | `core/java/android/widget/ImageView.java` | `configureBounds`, `onDraw` (as `atlDrawContent`), `drawableStateChanged`, `applyImageTint`, `applyColorMod` |
| `widget/CompoundButton.java` | `core/java/android/widget/CompoundButton.java` | `toggle`, `performClick`, `setChecked`, `onCreateDrawableState` |
| `widget/RadioButton.java` | `core/java/android/widget/RadioButton.java` | `toggle` |
| `text/Editable.java` | `core/java/android/text/Editable.java` | the whole file |
| `text/Html.java` | `core/java/android/text/Html.java` | the whole file without documentation and the CSS `style` attribute's text-align, text-decoration and background; `ATLHtmlReader` (ATL's) parses in place of TagSoup's `Parser`, and a missing image is an empty `ColorDrawable` |
| `text/style/` | `core/java/android/text/style/` | `CharacterStyle`, `MetricAffectingSpan`, `ForegroundColorSpan`, `BackgroundColorSpan`, `UnderlineSpan`, `StrikethroughSpan`, `RelativeSizeSpan`, `AbsoluteSizeSpan`, `ScaleXSpan`, `SuperscriptSpan`, `SubscriptSpan`, `LocaleSpan`, `StyleSpan`, `TypefaceSpan`, `TextAppearanceSpan`, `ClickableSpan`, `URLSpan`: their constructors, getters and `updateDrawState` / `updateMeasureState`, without parcelling; `AlignmentSpan`, `BulletSpan`, `QuoteSpan` without drawing |
| `text/InputType.java` | `core/java/android/text/InputType.java` | the whole file |
| `text/InputFilter.java` | `core/java/android/text/InputFilter.java` | the whole file; `AllCaps` upper-cases with `String.toUpperCase` (no ICU `CaseMap`) and keeps spans only when the length stays |
| `view/inputmethod/EditorInfo.java` | `core/java/android/view/inputmethod/EditorInfo.java` | the `IME_*` constants, `implements InputType` |
| `widget/TextView.java` | `core/java/android/widget/TextView.java` | `onEditorAction` (its Enter keys go through `ATLInput.dispatchKeyFromIme`), `getSelectionStart`, `getSelectionEnd`, `hasSelection`; the line and height limits (`setMaxLines`, `setMaxHeight`, `setMinLines`, `setMinHeight`, `setLines`, `setHeight`, one maximum and one minimum mode), `applySingleLine` without the transformation and horizontal scrolling, `getLineHeight` |
| `widget/EditText.java` | `core/java/android/widget/EditText.java` | `getText`, `setSelection`, `selectAll`, `extendSelection` |
| `widget/EditText.java` | `core/java/android/widget/TextView.java` | `setText`'s filters, Editable and watcher calls, the ChangeWatcher's TextWatcher part, `sendBeforeTextChanged`, `sendOnTextChanged`, `sendAfterTextChanged`, `isMultilineInputType`, `isPasswordInputType`, `setInputTypeSingleLine`, the constructor's `inputType`, `singleLine`, `password`, `maxLength` and `digits` handling, `shouldAdvanceFocusOnEnter`, `onKeyDown` and `onKeyUp`'s Enter; `setKeyListener`, `setInputType`'s number and phone listeners, `setFilters(Editable, InputFilter[])`'s key listener after the app's filters |
| `text/method/KeyListener.java` | `core/java/android/text/method/KeyListener.java` | the whole file |
| `text/method/NumberKeyListener.java` | `core/java/android/text/method/NumberKeyListener.java` | the filter: `getAcceptedChars`, `filter`, `ok`, `addDigits`, `collectionToArray`; no key handling (`lookup`, `onKeyDown`) or date and time characters. ATL's `BaseKeyListener` and `MetaKeyKeyListener` take no keys: hardware keys reach the toolkit's field |
| `text/method/DigitsKeyListener.java` | `core/java/android/text/method/DigitsKeyListener.java` | the whole file without `@GuardedBy` |
| `text/method/DialerKeyListener.java` | `core/java/android/text/method/DialerKeyListener.java` | the whole file without `lookup` |
| `src/api-impl/com/android/internal/util/ArrayUtils.java` | `core/java/com/android/internal/util/ArrayUtils.java` | `contains(char[], char)`, `containsAll(char[], char[])` |

Open: Dine's app bundle carries `api-impl.jar` without these notices, `NOTICE-AOSP.txt` or the
Apache text.
