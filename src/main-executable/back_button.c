#include "back_button.h"

#include "src/shared/nav.h"

extern struct atl_app *host_app; // main.c

GtkWidget *back_button;

static void back_clicked(GtkButton *button, gpointer user_data)
{
	atl_nav_back(host_app);
}

GtkWidget *back_button_new()
{
	back_button = gtk_button_new_from_icon_name("go-previous");
	// GTK focus on it would take Enter and Space from the app's key chain
	gtk_widget_set_focusable(back_button, FALSE);

	g_signal_connect(back_button, "clicked", G_CALLBACK(back_clicked), NULL);

	back_button_set_sensitive(false);
	return back_button;
}

void back_button_set_sensitive(bool sensitive)
{
	gtk_widget_set_sensitive(GTK_WIDGET(back_button), sensitive);
}
