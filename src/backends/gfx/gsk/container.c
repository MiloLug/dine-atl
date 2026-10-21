#include <gdk/gdk.h>

#include "src/shared/test_hooks.h"
#include "src/slots/gfx.h"

/* A container's paintable shows the selected child's. */

struct _ContainerPaintable {
	GObject parent_instance;
	GdkPaintable *child;
};
G_DECLARE_FINAL_TYPE(ContainerPaintable, container_paintable, CONTAINER, PAINTABLE, GObject)

static void container_paintable_snapshot(GdkPaintable *paintable, GdkSnapshot *snapshot, double width, double height)
{
	ContainerPaintable *container = CONTAINER_PAINTABLE(paintable);
	if (container->child)
		gdk_paintable_snapshot(container->child, snapshot, width, height);
}

static double container_paintable_get_intrinsic_aspect_ratio(GdkPaintable *paintable)
{
	ContainerPaintable *container = CONTAINER_PAINTABLE(paintable);
	if (container->child)
		return gdk_paintable_get_intrinsic_aspect_ratio(container->child);
	else
		return 0;
}

static int container_paintable_get_intrinsic_width(GdkPaintable *paintable)
{
	ContainerPaintable *container = CONTAINER_PAINTABLE(paintable);
	if (container->child)
		return gdk_paintable_get_intrinsic_width(container->child);
	else
		return 0;
}

static int container_paintable_get_intrinsic_height(GdkPaintable *paintable)
{
	ContainerPaintable *container = CONTAINER_PAINTABLE(paintable);
	if (container->child)
		return gdk_paintable_get_intrinsic_height(container->child);
	else
		return 0;
}

static void container_paintable_init(ContainerPaintable *container_paintable)
{
}

static void container_paintable_paintable_init(GdkPaintableInterface *iface)
{
	iface->snapshot = container_paintable_snapshot;
	iface->get_intrinsic_aspect_ratio = container_paintable_get_intrinsic_aspect_ratio;
	iface->get_intrinsic_width = container_paintable_get_intrinsic_width;
	iface->get_intrinsic_height = container_paintable_get_intrinsic_height;
}

G_DEFINE_TYPE_WITH_CODE(ContainerPaintable, container_paintable, G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(GDK_TYPE_PAINTABLE, container_paintable_paintable_init))

static void container_paintable_dispose(GObject *object)
{
	g_clear_object(&CONTAINER_PAINTABLE(object)->child);
	G_OBJECT_CLASS(container_paintable_parent_class)->dispose(object);
}

static void container_paintable_finalize(GObject *object)
{
	atl_test_drawable_freed(G_OBJECT_TYPE_NAME(object));
	G_OBJECT_CLASS(container_paintable_parent_class)->finalize(object);
}

static void container_paintable_class_init(ContainerPaintableClass *class)
{
	G_OBJECT_CLASS(class)->dispose = container_paintable_dispose;
	G_OBJECT_CLASS(class)->finalize = container_paintable_finalize;
}

struct gfx_drawable *gfx_container_new(void)
{
	return (struct gfx_drawable *)g_object_new(container_paintable_get_type(), NULL);
}

void gfx_container_select(struct gfx_drawable *drawable, struct gfx_drawable *child)
{
	ContainerPaintable *container = CONTAINER_PAINTABLE(drawable);
	GdkPaintable *paintable = (GdkPaintable *)child; // NULL: none
	g_set_object(&container->child, paintable);
	gdk_paintable_invalidate_contents(GDK_PAINTABLE(container));
	gdk_paintable_invalidate_size(GDK_PAINTABLE(container));
}
