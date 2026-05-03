#ifndef INPUT_H
#define INPUT_H

#include <gtk/gtk.h>

struct input_queue {
	int fd;
	GtkEventController *controller;
};

#endif
