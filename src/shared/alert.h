#ifndef SHARED_ALERT_H
#define SHARED_ALERT_H

#include <stdbool.h>

#include "src/slots/ui.h"

/* The shown alert a backend keeps: what ui_dialog_show gave it, with the slot's changes since. A copy
 * owns its strings and checks. */
void atl_alert_copy(struct ui_alert *copy, const struct ui_alert *alert);
void atl_alert_clear(struct ui_alert *alert);
// which: DialogInterface.BUTTON_*; no label hides the button
void atl_alert_set_button(struct ui_alert *alert, int which, const char *label, bool enabled);
// true when it changes between a bar and a spinner
bool atl_alert_set_progress(struct ui_alert *alert, int value, int max, bool indeterminate);
/* A tap on row moves the checks as Android's choice list does: a single choice's to the row, a multi
 * choice's row flips; the row's check after it. */
bool atl_alert_tap_row(struct ui_alert *alert, int row);
/* AOSP 10's alert in max_height above a footer this tall: the most its header (title, progress, and
 * the message or the rows) takes, half of the room beside Android content. The title and the
 * progress keep their height; the message or the rows scroll in what they leave. Any unit. */
float atl_alert_header_cap(float max_height, float footer, bool content);

#endif
