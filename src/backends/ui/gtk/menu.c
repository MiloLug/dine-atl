#include <gtk/gtk.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/shared/menu.h"
#include "src/shared/test_hooks.h"
#include "src/slots/ui.h"

/* A PopupMenu as a GtkPopoverMenu on its anchor: an action per item, stateful for a checkable one.
 * GTK may close the popover before or after it activates an item's action: the dismissal waits for
 * an idle, so a picked item answers first. */

struct gtk_menu {
	struct atl_menu *menu;
	GtkWidget *popover;
	bool answered;     // its one answer went
	guint closed_idle;   // the dismissal waiting
	bool script_waiting; // a scripted answer waiting
	bool freed;          // ui_menu_free came while an idle waited
};

static void answer(struct gtk_menu *shown, int index)
{
	if (shown->answered)
		return;
	shown->answered = true;
	atl_menu_answer(shown->menu, index);
}

static void activated(GSimpleAction *action, GVariant *parameter, gpointer data)
{
	answer(data, GPOINTER_TO_INT(g_object_get_data(G_OBJECT(action), "atl-index")));
}

static gboolean closed_idle(gpointer data)
{
	struct gtk_menu *shown = data;
	shown->closed_idle = 0;
	answer(shown, UI_MENU_DISMISSED);
	if (shown->freed && !shown->script_waiting)
		free(shown);
	return G_SOURCE_REMOVE;
}

static void closed(GtkPopover *popover, gpointer data)
{
	struct gtk_menu *shown = data;
	if (!shown->closed_idle)
		shown->closed_idle = g_idle_add(closed_idle, shown);
	shown->popover = NULL; // the unparent drops its last reference
	gtk_widget_unparent(GTK_WIDGET(popover));
}

struct scripted {
	struct gtk_menu *shown;
	GActionGroup *group;
	int index;
};

// as a click on the item would
static gboolean scripted_answer(gpointer data)
{
	struct scripted *script = data;
	if (script->index != UI_MENU_DISMISSED && !script->shown->freed) {
		char name[32];
		snprintf(name, sizeof(name), "i%d", script->index);
		g_action_group_activate_action(script->group, name, NULL);
	}
	struct gtk_menu *shown = script->shown;
	shown->script_waiting = false;
	if (shown->freed) {
		if (!shown->closed_idle)
			free(shown);
	} else if (shown->popover) {
		gtk_popover_popdown(GTK_POPOVER(shown->popover));
	}
	g_object_unref(script->group);
	g_free(script);
	return G_SOURCE_REMOVE;
}

void *ui_menu_show(struct ui_window *window, struct ui_view *anchor, const struct ui_menu_item *items, int n, struct atl_menu *menu)
{
	struct gtk_menu *shown = calloc(1, sizeof(*shown));
	shown->menu = menu;
	GSimpleActionGroup *group = g_simple_action_group_new();
	GMenu **menus = g_new0(GMenu *, n + 1); // per item with a submenu, its model; the top one last
	menus[n] = g_menu_new();
	for (int i = 0; i < n; i++) {
		if (items[i].has_submenu)
			menus[i] = g_menu_new();
	}
	for (int i = 0; i < n; i++) {
		const struct ui_menu_item *item = &items[i];
		GMenu *in = menus[item->parent >= 0 ? item->parent : n];
		if (item->has_submenu) {
			g_menu_append_submenu(in, item->title, G_MENU_MODEL(menus[i]));
			continue;
		}
		char name[32];
		snprintf(name, sizeof(name), "i%d", i);
		GSimpleAction *action = item->checkable ? g_simple_action_new_stateful(name, NULL, g_variant_new_boolean(item->checked))
		                                        : g_simple_action_new(name, NULL);
		g_object_set_data(G_OBJECT(action), "atl-index", GINT_TO_POINTER(i));
		g_simple_action_set_enabled(action, item->enabled);
		g_signal_connect(action, "activate", G_CALLBACK(activated), shown);
		g_action_map_add_action(G_ACTION_MAP(group), G_ACTION(action));
		g_object_unref(action);
		char detailed[40];
		snprintf(detailed, sizeof(detailed), "menu.%s", name);
		g_menu_append(in, item->title, detailed);
	}
	shown->popover = gtk_popover_menu_new_from_model(G_MENU_MODEL(menus[n]));
	for (int i = 0; i <= n; i++) {
		if (menus[i])
			g_object_unref(menus[i]);
	}
	g_free(menus);
	gtk_widget_insert_action_group(shown->popover, "menu", G_ACTION_GROUP(group));
	g_signal_connect(shown->popover, "closed", G_CALLBACK(closed), shown);
	gtk_widget_insert_before(shown->popover, gtk_widget_get_parent(GTK_WIDGET(anchor)), NULL);
	gtk_popover_present(GTK_POPOVER(shown->popover));
	gtk_popover_popup(GTK_POPOVER(shown->popover));
#ifdef ATL_TEST_HOOKS
	printf("menu: presented %d\n", n);
	fflush(stdout);
#endif
	int index;
	if (atl_menu_scripted(items, n, &index)) {
		struct scripted *script = g_new(struct scripted, 1);
		*script = (struct scripted){shown, G_ACTION_GROUP(g_object_ref(group)), index};
		shown->script_waiting = true;
		g_idle_add(scripted_answer, script);
	}
	g_object_unref(group);
	return shown;
}

void ui_menu_close(void *menu)
{
	struct gtk_menu *shown = menu;
	if (shown->popover)
		gtk_popover_popdown(GTK_POPOVER(shown->popover));
}

void ui_menu_free(void *menu)
{
	struct gtk_menu *shown = menu;
	if (shown->closed_idle || shown->script_waiting)
		shown->freed = true;
	else
		free(shown);
}
