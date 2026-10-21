#import "input.h"

// USB HID keyboard usages to Android's KeyEvent codes and meta flags

#define KEYCODE_UNKNOWN 0
#define KEYCODE_0       7
#define KEYCODE_1       8
#define KEYCODE_A       29
#define KEYCODE_F1      131
#define KEYCODE_NUMPAD_0 144
#define KEYCODE_NUMPAD_1 145

#define META_SHIFT_ON     0x1
#define META_ALT_ON       0x2
#define META_CTRL_ON      0x1000
#define META_META_ON      0x10000
#define META_CAPS_LOCK_ON 0x100000

int keys_android_code(UIKeyboardHIDUsage usage)
{
	if (usage >= UIKeyboardHIDUsageKeyboardA && usage <= UIKeyboardHIDUsageKeyboardZ)
		return KEYCODE_A + (usage - UIKeyboardHIDUsageKeyboardA);
	if (usage >= UIKeyboardHIDUsageKeyboard1 && usage <= UIKeyboardHIDUsageKeyboard9)
		return KEYCODE_1 + (usage - UIKeyboardHIDUsageKeyboard1);
	if (usage >= UIKeyboardHIDUsageKeyboardF1 && usage <= UIKeyboardHIDUsageKeyboardF12)
		return KEYCODE_F1 + (usage - UIKeyboardHIDUsageKeyboardF1);
	if (usage >= UIKeyboardHIDUsageKeypad1 && usage <= UIKeyboardHIDUsageKeypad9)
		return KEYCODE_NUMPAD_1 + (usage - UIKeyboardHIDUsageKeypad1);
	switch (usage) {
		case UIKeyboardHIDUsageKeyboard0:
			return KEYCODE_0;
		case UIKeyboardHIDUsageKeypad0:
			return KEYCODE_NUMPAD_0;
		case UIKeyboardHIDUsageKeyboardReturnOrEnter:
			return 66; // ENTER
		case UIKeyboardHIDUsageKeyboardEscape:
			return 111; // ESCAPE
		case UIKeyboardHIDUsageKeyboardDeleteOrBackspace:
			return 67; // DEL
		case UIKeyboardHIDUsageKeyboardTab:
			return 61; // TAB
		case UIKeyboardHIDUsageKeyboardSpacebar:
			return 62; // SPACE
		case UIKeyboardHIDUsageKeyboardHyphen:
			return 69; // MINUS
		case UIKeyboardHIDUsageKeyboardEqualSign:
			return 70; // EQUALS
		case UIKeyboardHIDUsageKeyboardOpenBracket:
			return 71; // LEFT_BRACKET
		case UIKeyboardHIDUsageKeyboardCloseBracket:
			return 72; // RIGHT_BRACKET
		case UIKeyboardHIDUsageKeyboardBackslash:
			return 73; // BACKSLASH
		case UIKeyboardHIDUsageKeyboardSemicolon:
			return 74; // SEMICOLON
		case UIKeyboardHIDUsageKeyboardQuote:
			return 75; // APOSTROPHE
		case UIKeyboardHIDUsageKeyboardGraveAccentAndTilde:
			return 68; // GRAVE
		case UIKeyboardHIDUsageKeyboardComma:
			return 55; // COMMA
		case UIKeyboardHIDUsageKeyboardPeriod:
			return 56; // PERIOD
		case UIKeyboardHIDUsageKeyboardSlash:
			return 76; // SLASH
		case UIKeyboardHIDUsageKeyboardCapsLock:
			return 115; // CAPS_LOCK
		case UIKeyboardHIDUsageKeyboardInsert:
			return 124; // INSERT
		case UIKeyboardHIDUsageKeyboardHome:
			return 122; // MOVE_HOME
		case UIKeyboardHIDUsageKeyboardPageUp:
			return 92; // PAGE_UP
		case UIKeyboardHIDUsageKeyboardDeleteForward:
			return 112; // FORWARD_DEL
		case UIKeyboardHIDUsageKeyboardEnd:
			return 123; // MOVE_END
		case UIKeyboardHIDUsageKeyboardPageDown:
			return 93; // PAGE_DOWN
		case UIKeyboardHIDUsageKeyboardRightArrow:
			return 22; // DPAD_RIGHT
		case UIKeyboardHIDUsageKeyboardLeftArrow:
			return 21; // DPAD_LEFT
		case UIKeyboardHIDUsageKeyboardDownArrow:
			return 20; // DPAD_DOWN
		case UIKeyboardHIDUsageKeyboardUpArrow:
			return 19; // DPAD_UP
		case UIKeyboardHIDUsageKeypadSlash:
			return 154; // NUMPAD_DIVIDE
		case UIKeyboardHIDUsageKeypadAsterisk:
			return 155; // NUMPAD_MULTIPLY
		case UIKeyboardHIDUsageKeypadHyphen:
			return 156; // NUMPAD_SUBTRACT
		case UIKeyboardHIDUsageKeypadPlus:
			return 157; // NUMPAD_ADD
		case UIKeyboardHIDUsageKeypadEnter:
			return 160; // NUMPAD_ENTER
		case UIKeyboardHIDUsageKeypadPeriod:
			return 158; // NUMPAD_DOT
		case UIKeyboardHIDUsageKeyboardLeftControl:
			return 113; // CTRL_LEFT
		case UIKeyboardHIDUsageKeyboardLeftShift:
			return 59; // SHIFT_LEFT
		case UIKeyboardHIDUsageKeyboardLeftAlt:
			return 57; // ALT_LEFT
		case UIKeyboardHIDUsageKeyboardLeftGUI:
			return 117; // META_LEFT
		case UIKeyboardHIDUsageKeyboardRightControl:
			return 114; // CTRL_RIGHT
		case UIKeyboardHIDUsageKeyboardRightShift:
			return 60; // SHIFT_RIGHT
		case UIKeyboardHIDUsageKeyboardRightAlt:
			return 58; // ALT_RIGHT
		case UIKeyboardHIDUsageKeyboardRightGUI:
			return 118; // META_RIGHT
		default:
			return KEYCODE_UNKNOWN;
	}
}

int keys_android_meta(UIKeyModifierFlags flags)
{
	int meta = 0;
	if (flags & UIKeyModifierShift)
		meta |= META_SHIFT_ON;
	if (flags & UIKeyModifierAlternate)
		meta |= META_ALT_ON;
	if (flags & UIKeyModifierControl)
		meta |= META_CTRL_ON;
	if (flags & UIKeyModifierCommand)
		meta |= META_META_ON;
	if (flags & UIKeyModifierAlphaShift)
		meta |= META_CAPS_LOCK_ON;
	return meta;
}
