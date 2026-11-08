#ifndef SHARED_MENU_H
#define SHARED_MENU_H

struct atl_menu;

/* A backend's answer for a menu ui_menu_show showed: the picked item's index or UI_MENU_DISMISSED,
 * once per menu. Any thread; Java gets it on the app's main thread, unless it dismissed the menu
 * first. */
void atl_menu_answer(struct atl_menu *menu, int index);

#endif
