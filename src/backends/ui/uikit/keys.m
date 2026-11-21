#import "input.h"

#include "src/shared/keys.h"

// USB HID keyboard usages to Android's KeyEvent codes and meta flags

int keys_android_code(UIKeyboardHIDUsage usage)
{
	if (usage >= UIKeyboardHIDUsageKeyboardA && usage <= UIKeyboardHIDUsageKeyboardZ)
		return ATL_KEYCODE_A + (usage - UIKeyboardHIDUsageKeyboardA);
	if (usage >= UIKeyboardHIDUsageKeyboard1 && usage <= UIKeyboardHIDUsageKeyboard9)
		return ATL_KEYCODE_0 + 1 + (usage - UIKeyboardHIDUsageKeyboard1);
	if (usage >= UIKeyboardHIDUsageKeyboardF1 && usage <= UIKeyboardHIDUsageKeyboardF12)
		return ATL_KEYCODE_F1 + (usage - UIKeyboardHIDUsageKeyboardF1);
	if (usage >= UIKeyboardHIDUsageKeypad1 && usage <= UIKeyboardHIDUsageKeypad9)
		return ATL_KEYCODE_NUMPAD_0 + 1 + (usage - UIKeyboardHIDUsageKeypad1);
	switch (usage) {
		case UIKeyboardHIDUsageKeyboard0:
			return ATL_KEYCODE_0;
		case UIKeyboardHIDUsageKeypad0:
			return ATL_KEYCODE_NUMPAD_0;
		case UIKeyboardHIDUsageKeyboardReturnOrEnter:
			return ATL_KEYCODE_ENTER;
		case UIKeyboardHIDUsageKeyboardEscape:
			return ATL_KEYCODE_ESCAPE;
		case UIKeyboardHIDUsageKeyboardDeleteOrBackspace:
			return ATL_KEYCODE_DEL;
		case UIKeyboardHIDUsageKeyboardTab:
			return ATL_KEYCODE_TAB;
		case UIKeyboardHIDUsageKeyboardSpacebar:
			return ATL_KEYCODE_SPACE;
		case UIKeyboardHIDUsageKeyboardHyphen:
			return ATL_KEYCODE_MINUS;
		case UIKeyboardHIDUsageKeyboardEqualSign:
			return ATL_KEYCODE_EQUALS;
		case UIKeyboardHIDUsageKeyboardOpenBracket:
			return ATL_KEYCODE_LEFT_BRACKET;
		case UIKeyboardHIDUsageKeyboardCloseBracket:
			return ATL_KEYCODE_RIGHT_BRACKET;
		case UIKeyboardHIDUsageKeyboardBackslash:
			return ATL_KEYCODE_BACKSLASH;
		case UIKeyboardHIDUsageKeyboardSemicolon:
			return ATL_KEYCODE_SEMICOLON;
		case UIKeyboardHIDUsageKeyboardQuote:
			return ATL_KEYCODE_APOSTROPHE;
		case UIKeyboardHIDUsageKeyboardGraveAccentAndTilde:
			return ATL_KEYCODE_GRAVE;
		case UIKeyboardHIDUsageKeyboardComma:
			return ATL_KEYCODE_COMMA;
		case UIKeyboardHIDUsageKeyboardPeriod:
			return ATL_KEYCODE_PERIOD;
		case UIKeyboardHIDUsageKeyboardSlash:
			return ATL_KEYCODE_SLASH;
		case UIKeyboardHIDUsageKeyboardCapsLock:
			return ATL_KEYCODE_CAPS_LOCK;
		case UIKeyboardHIDUsageKeyboardInsert:
			return ATL_KEYCODE_INSERT;
		case UIKeyboardHIDUsageKeyboardHome:
			return ATL_KEYCODE_MOVE_HOME;
		case UIKeyboardHIDUsageKeyboardPageUp:
			return ATL_KEYCODE_PAGE_UP;
		case UIKeyboardHIDUsageKeyboardDeleteForward:
			return ATL_KEYCODE_FORWARD_DEL;
		case UIKeyboardHIDUsageKeyboardEnd:
			return ATL_KEYCODE_MOVE_END;
		case UIKeyboardHIDUsageKeyboardPageDown:
			return ATL_KEYCODE_PAGE_DOWN;
		case UIKeyboardHIDUsageKeyboardRightArrow:
			return ATL_KEYCODE_DPAD_RIGHT;
		case UIKeyboardHIDUsageKeyboardLeftArrow:
			return ATL_KEYCODE_DPAD_LEFT;
		case UIKeyboardHIDUsageKeyboardDownArrow:
			return ATL_KEYCODE_DPAD_DOWN;
		case UIKeyboardHIDUsageKeyboardUpArrow:
			return ATL_KEYCODE_DPAD_UP;
		case UIKeyboardHIDUsageKeypadSlash:
			return ATL_KEYCODE_NUMPAD_DIVIDE;
		case UIKeyboardHIDUsageKeypadAsterisk:
			return ATL_KEYCODE_NUMPAD_MULTIPLY;
		case UIKeyboardHIDUsageKeypadHyphen:
			return ATL_KEYCODE_NUMPAD_SUBTRACT;
		case UIKeyboardHIDUsageKeypadPlus:
			return ATL_KEYCODE_NUMPAD_ADD;
		case UIKeyboardHIDUsageKeypadEnter:
			return ATL_KEYCODE_NUMPAD_ENTER;
		case UIKeyboardHIDUsageKeypadPeriod:
			return ATL_KEYCODE_NUMPAD_DOT;
		case UIKeyboardHIDUsageKeyboardLeftControl:
			return ATL_KEYCODE_CTRL_LEFT;
		case UIKeyboardHIDUsageKeyboardLeftShift:
			return ATL_KEYCODE_SHIFT_LEFT;
		case UIKeyboardHIDUsageKeyboardLeftAlt:
			return ATL_KEYCODE_ALT_LEFT;
		case UIKeyboardHIDUsageKeyboardLeftGUI:
			return ATL_KEYCODE_META_LEFT;
		case UIKeyboardHIDUsageKeyboardRightControl:
			return ATL_KEYCODE_CTRL_RIGHT;
		case UIKeyboardHIDUsageKeyboardRightShift:
			return ATL_KEYCODE_SHIFT_RIGHT;
		case UIKeyboardHIDUsageKeyboardRightAlt:
			return ATL_KEYCODE_ALT_RIGHT;
		case UIKeyboardHIDUsageKeyboardRightGUI:
			return ATL_KEYCODE_META_RIGHT;
		default:
			return ATL_KEYCODE_UNKNOWN;
	}
}

int keys_android_meta(UIKeyModifierFlags flags)
{
	int meta = 0;
	if (flags & UIKeyModifierShift)
		meta |= ATL_META_SHIFT_ON;
	if (flags & UIKeyModifierAlternate)
		meta |= ATL_META_ALT_ON;
	if (flags & UIKeyModifierControl)
		meta |= ATL_META_CTRL_ON;
	if (flags & UIKeyModifierCommand)
		meta |= ATL_META_META_ON;
	if (flags & UIKeyModifierAlphaShift)
		meta |= ATL_META_CAPS_LOCK_ON;
	return meta;
}
