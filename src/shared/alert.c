#include <stdlib.h>
#include <string.h>

#include "alert.h"

static char *dup(const char *chars)
{
	return chars ? strdup(chars) : NULL;
}

void atl_alert_clear(struct ui_alert *alert)
{
	free((char *)alert->title);
	free((char *)alert->message);
	for (int i = 0; i < alert->n_items; i++)
		free((char *)alert->items[i]);
	free((char **)alert->items);
	free((bool *)alert->checked);
	for (int i = 0; i < 3; i++)
		free((char *)alert->buttons[i]);
	memset(alert, 0, sizeof(*alert));
}

void atl_alert_copy(struct ui_alert *copy, const struct ui_alert *alert)
{
	*copy = *alert;
	copy->title = dup(alert->title);
	copy->message = dup(alert->message);
	if (alert->items) {
		char **items = calloc(alert->n_items ? alert->n_items : 1, sizeof(*items));
		for (int i = 0; i < alert->n_items; i++)
			items[i] = dup(alert->items[i]);
		copy->items = (const char *const *)items;
	}
	if (alert->checked)
		copy->checked = memcpy(calloc(alert->n_items ? alert->n_items : 1, sizeof(bool)), alert->checked, alert->n_items * sizeof(bool));
	for (int i = 0; i < 3; i++)
		copy->buttons[i] = dup(alert->buttons[i]);
}

void atl_alert_set_button(struct ui_alert *alert, int which, const char *label, bool enabled)
{
	int i = -1 - which;
	free((char *)alert->buttons[i]);
	alert->buttons[i] = dup(label);
	alert->disabled[i] = !enabled;
}

bool atl_alert_set_progress(struct ui_alert *alert, int value, int max, bool indeterminate)
{
	bool restyled = alert->indeterminate != indeterminate;
	alert->value = value;
	alert->max = max;
	alert->indeterminate = indeterminate;
	return restyled;
}

bool atl_alert_tap_row(struct ui_alert *alert, int row)
{
	bool *checked = (bool *)alert->checked;
	if (alert->check == UI_ALERT_CHECK_NONE || !checked || row < 0 || row >= alert->n_items)
		return false;
	bool now = alert->check == UI_ALERT_CHECK_MULTI ? !checked[row] : true;
	if (alert->check == UI_ALERT_CHECK_SINGLE)
		memset(checked, 0, alert->n_items * sizeof(*checked));
	checked[row] = now;
	return now;
}

float atl_alert_header_cap(float max_height, float footer, bool content)
{
	float room = max_height - footer;
	return content ? room / 2 : room;
}
