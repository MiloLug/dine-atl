#include <stdio.h>
#include <stdlib.h>

#include "src/shared/menu.h"
#include "src/shared/test_hooks.h"
#include "src/slots/ui.h"

/* A PopupMenu as "menu:" lines, answered from ATL_DIALOG_ANSWERS. */

struct headless_menu {
	struct atl_menu *menu;
	bool answered; // its one answer went
};

static void answer(struct headless_menu *shown, int index)
{
	if (shown->answered)
		return;
	shown->answered = true;
	printf("menu: answer %d\n", index);
	fflush(stdout);
	atl_menu_answer(shown->menu, index);
}

void *ui_menu_show(struct ui_window *window, struct ui_view *anchor, const struct ui_menu_item *items, int n, struct atl_menu *menu)
{
	struct headless_menu *shown = calloc(1, sizeof(*shown));
	shown->menu = menu;
	printf("menu: show %d\n", n);
	for (int i = 0; i < n; i++) {
		const struct ui_menu_item *item = &items[i];
		printf("menu: item %d \"%s\"", i, item->title);
		if (item->parent >= 0)
			printf(" in %d", item->parent);
		printf("%s%s%s%s\n", item->enabled ? "" : " disabled", item->checkable ? " checkable" : "", item->checked ? " checked" : "",
		       item->has_submenu ? " submenu" : "");
	}
	fflush(stdout);
	int index;
	if (atl_menu_scripted(items, n, &index))
		answer(shown, index);
	return shown;
}

void ui_menu_close(void *menu)
{
	printf("menu: close\n");
	fflush(stdout);
	answer(menu, UI_MENU_DISMISSED);
}

void ui_menu_free(void *menu)
{
	free(menu);
}
