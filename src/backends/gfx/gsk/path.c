#include <gtk/gtk.h>

#include "src/shared/path.h"

#include "canvas.h"

GskPath *gsk_path_from_gfx(const struct gfx_path *path)
{
	GskPathBuilder *builder = gsk_path_builder_new();
	const float *p = path->points;
	for (size_t i = 0; i < path->n_verbs; i++) {
		switch (path->verbs[i]) {
		case PATH_MOVE: gsk_path_builder_move_to(builder, p[0], p[1]); break;
		case PATH_LINE: gsk_path_builder_line_to(builder, p[0], p[1]); break;
		case PATH_QUAD: gsk_path_builder_quad_to(builder, p[0], p[1], p[2], p[3]); break;
		case PATH_CUBIC: gsk_path_builder_cubic_to(builder, p[0], p[1], p[2], p[3], p[4], p[5]); break;
		case PATH_CLOSE: gsk_path_builder_close(builder); break;
		}
		p += path_verb_points(path->verbs[i]) * 2;
	}
	return gsk_path_builder_free_to_path(builder);
}
