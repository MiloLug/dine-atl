#ifndef SHARED_KEYS_H
#define SHARED_KEYS_H

/* Android's KeyEvent.KEYCODE_* and META_* values, for the backends' key maps. */

#define ATL_KEYCODE_UNKNOWN         0
#define ATL_KEYCODE_BACK            4
#define ATL_KEYCODE_0               7 // KEYCODE_1 to KEYCODE_9 follow
#define ATL_KEYCODE_DPAD_UP         19
#define ATL_KEYCODE_DPAD_DOWN       20
#define ATL_KEYCODE_DPAD_LEFT       21
#define ATL_KEYCODE_DPAD_RIGHT      22
#define ATL_KEYCODE_A               29 // KEYCODE_B to KEYCODE_Z follow
#define ATL_KEYCODE_COMMA           55
#define ATL_KEYCODE_PERIOD          56
#define ATL_KEYCODE_ALT_LEFT        57
#define ATL_KEYCODE_ALT_RIGHT       58
#define ATL_KEYCODE_SHIFT_LEFT      59
#define ATL_KEYCODE_SHIFT_RIGHT     60
#define ATL_KEYCODE_TAB             61
#define ATL_KEYCODE_SPACE           62
#define ATL_KEYCODE_ENTER           66
#define ATL_KEYCODE_DEL             67
#define ATL_KEYCODE_GRAVE           68
#define ATL_KEYCODE_MINUS           69
#define ATL_KEYCODE_EQUALS          70
#define ATL_KEYCODE_LEFT_BRACKET    71
#define ATL_KEYCODE_RIGHT_BRACKET   72
#define ATL_KEYCODE_BACKSLASH       73
#define ATL_KEYCODE_SEMICOLON       74
#define ATL_KEYCODE_APOSTROPHE      75
#define ATL_KEYCODE_SLASH           76
#define ATL_KEYCODE_AT              77
#define ATL_KEYCODE_PLUS            81
#define ATL_KEYCODE_PAGE_UP         92
#define ATL_KEYCODE_PAGE_DOWN       93
#define ATL_KEYCODE_ESCAPE          111
#define ATL_KEYCODE_FORWARD_DEL     112
#define ATL_KEYCODE_CTRL_LEFT       113
#define ATL_KEYCODE_CTRL_RIGHT      114
#define ATL_KEYCODE_CAPS_LOCK       115
#define ATL_KEYCODE_META_LEFT       117
#define ATL_KEYCODE_META_RIGHT      118
#define ATL_KEYCODE_MOVE_HOME       122
#define ATL_KEYCODE_MOVE_END        123
#define ATL_KEYCODE_INSERT          124
#define ATL_KEYCODE_F1              131 // KEYCODE_F2 to KEYCODE_F12 follow
#define ATL_KEYCODE_NUMPAD_0        144 // KEYCODE_NUMPAD_1 to KEYCODE_NUMPAD_9 follow
#define ATL_KEYCODE_NUMPAD_DIVIDE   154
#define ATL_KEYCODE_NUMPAD_MULTIPLY 155
#define ATL_KEYCODE_NUMPAD_SUBTRACT 156
#define ATL_KEYCODE_NUMPAD_ADD      157
#define ATL_KEYCODE_NUMPAD_DOT      158
#define ATL_KEYCODE_NUMPAD_ENTER    160

#define ATL_META_SHIFT_ON     0x1
#define ATL_META_ALT_ON       0x2
#define ATL_META_CTRL_ON      0x1000
#define ATL_META_META_ON      0x10000
#define ATL_META_CAPS_LOCK_ON 0x100000

#endif
