project('android_translation_layer', 'c', default_options: ['b_lundef=false'])

fs = import('fs')

incdir_dep = declare_dependency(include_directories: '.')
add_project_dependencies(incdir_dep, language: 'c')

cc = meson.get_compiler('c')
dir_base = meson.current_source_dir()
builddir_base = meson.current_build_dir()
# Apple builds are cross builds producing an archive: the cross file puts ART's headers (jni.h,
# androidfw) on the include path, sqlite3.h comes with the SDK, the app links the libraries
apple = host_machine.system() in ['darwin', 'ios']
if apple
  libart_dep = declare_dependency()
  art_incdir_dep = declare_dependency()
  jni_dep = declare_dependency()
  sqlite3_dep = cc.find_library('sqlite3', has_headers: 'sqlite3.h')
else
  libart_dep = dependency('art-standalone')
  art_incdir_dep = declare_dependency(include_directories: libart_dep.get_variable(pkgconfig: 'includedir'))
  jni_dep = dependency('jni')
  sqlite3_dep = dependency('sqlite3')
endif

host_backend = get_option('host')
loop_backend = get_option('loop')
ui_backend = get_option('ui')
gfx_backend = get_option('gfx')

# the legacy GTK natives need the whole desktop set: GTK host and ui, glib loop, GSK drawing
desktop = ui_backend == 'gtk'
if (host_backend == 'gtk') != desktop or (gfx_backend == 'gsk') != desktop or (desktop and loop_backend != 'glib')
  error('host=gtk, ui=gtk, gfx=gsk and loop=glib go together')
endif
if (loop_backend == 'cfrunloop') != apple
  error('loop=cfrunloop is the loop for Apple targets, and only for them')
endif
if ui_backend == 'uikit'
  if not apple
    error('ui=uikit is for Apple targets')
  endif
  add_languages('objc', native: false)
  add_project_dependencies(incdir_dep, language: 'objc')
  add_project_arguments('-fobjc-arc', language: 'objc')
endif

# code shared by every build: JNI, ART/libcore, androidfw, sqlite and libc only
atl_shared = static_library('atl_shared', [
	'src/api-impl-jni/android_content_res_AssetManager.c',
	'src/api-impl-jni/android_os_Environment.c',
	'src/api-impl-jni/android_os_SystemClock.c',
	'src/api-impl-jni/android_util_Log.c',
	'src/api-impl-jni/content/android_content_res_XmlBlock.c',
	'src/api-impl-jni/database/android_database_SQLiteCommon.c',
	'src/api-impl-jni/database/android_database_SQLiteConnection.c',
	'src/api-impl-jni/database/android_database_SQLiteGlobal.c',
	'src/api-impl-jni/android_os_MessageQueue.c',
	'src/api-impl-jni/handle_cache.c',
	'src/api-impl-jni/os/android_os_Process.c',
	'src/api-impl-jni/util.c',
	'src/shared/activity.c',
	'src/shared/android_app_Dialog.c',
	'src/shared/android_content_Context.c',
	'src/shared/android_graphics_Paint.c',
	'src/shared/android_graphics_drawable_Drawable.c',
	'src/shared/android_view_View.c',
	'src/shared/android_view_ViewGroup.c',
	'src/shared/android_view_Window.c',
	'src/shared/android_widget_TextView.c',
	'src/shared/dialog_script.c',
	'src/shared/startup.c',
	'src/shared/upcall.c',
], dependencies: [jni_dep, sqlite3_dep, art_incdir_dep],
   c_args: ['-D_LARGEFILE64_SOURCE'])

backend_libs = []
legacy_srcs = []
legacy_deps = []
legacy_link_with = []
legacy_link_args = []
legacy_c_args = []

if desktop
  gnome = import('gnome')

  marshal_files = gnome.genmarshal('marshal',
  	sources: 'src/api-impl-jni/widgets/marshal.list',
  	valist_marshallers: true,
  	internal: true,
  )

  mpris = gnome.gdbus_codegen('mpris-dbus',
                              'src/api-impl-jni/media/org.mpris.MediaPlayer2.xml',
                              interface_prefix: 'org.mpris')
  portal_openuri = gnome.gdbus_codegen('portal-openuri',
                                       'src/api-impl-jni/content/org.freedesktop.portal.OpenURI.xml',
                                       interface_prefix: 'org.freedesktop.portal')
  portal_email = gnome.gdbus_codegen('portal-email',
                                     'src/api-impl-jni/content/org.freedesktop.portal.Email.xml',
                                     interface_prefix: 'org.freedesktop.portal')
  unifiedpush_distributor = gnome.gdbus_codegen('unifiedpush-distributor',
                                                'src/api-impl-jni/content/org.unifiedpush.Distributor1.xml',
                                                interface_prefix: 'org.unifiedpush')
  unifiedpush_connector = gnome.gdbus_codegen('unifiedpush-connector',
                                              'src/api-impl-jni/content/org.unifiedpush.Connector1.xml',
                                              interface_prefix: 'org.unifiedpush')

  extra_deps = []
  extra_jni_srcs = []

  layer_shell_dep = dependency('gtk4-layer-shell-0', required : false)
  if layer_shell_dep.found()
    extra_deps = [ layer_shell_dep ]
    extra_jni_srcs = [ 'src/api-impl-jni/app/android_atl_ATLKeyboardDialog.c' ]
    legacy_c_args += '-DATL_HAS_OSK'
  endif

  # libandroid
  libandroid_so = shared_library('android', [
                                            	'src/libandroid/asset_manager.c',
                                            	'src/libandroid/bitmap.c',
                                            	'src/libandroid/configuration.c',
                                            	'src/libandroid/egl.c',
                                            	'src/libandroid/input.c',
                                            	'src/libandroid/looper.c',
                                            	'src/libandroid/media.c',
                                            	'src/libandroid/native_window.c',
                                            	'src/libandroid/sensor.c',
                                            	'src/libandroid/trace.c',
                                            ],
                                            install: true,
                                            soversion: 0,
                                            dependencies: [
                                            	dependency('gtk4'), dependency('jni'), dependency('vulkan'), dependency('openxr').partial_dependency(includes: true),
                                            	art_incdir_dep
                                            ],
                                            c_args: [
                                            	'-D_LARGEFILE64_SOURCE',
                                            ],
                                            link_args : [
                                            	'-Wl,-z,lazy',  # outdated Nvidia driver version 340 lacks EGL symbols
                                            ])

  wayland_protos_dep = dependency('wayland-protocols', version: '>=1.12')

  # wayland protocols for osk
  subdir('protocol')

  # natives not yet behind a slot, built only with ui=gtk
  legacy_srcs = [
    'src/api-impl-jni/android_app_NativeActivity.c',
    'src/api-impl-jni/android_opengl_GLES20.c',
    'src/api-impl-jni/android_opengl_GLUtils.c',
    'src/api-impl-jni/android_os_MemoryFile.c',
    'src/api-impl-jni/android_os_Vibrator.c',
    'src/api-impl-jni/android_view_Window.c',
    'src/api-impl-jni/app/android_app_Activity.c',
    'src/api-impl-jni/app/android_app_NotificationManager.c',
    'src/api-impl-jni/app/android_app_WallpaperManager.c',
    'src/api-impl-jni/android_inputmethodservice_InputMethodService.c',
    'src/api-impl-jni/AssetInputStream.c',
    'src/api-impl-jni/audio/android_media_AudioManager.c',
    'src/api-impl-jni/audio/android_media_AudioRecord.c',
    'src/api-impl-jni/audio/android_media_AudioTrack.c',
    'src/api-impl-jni/audio/android_media_SoundPool.c',
    'src/api-impl-jni/audio/audio.c',
    'src/api-impl-jni/com_google_android_gles_jni_GLImpl.c',
    'src/api-impl-jni/content/android_atl_ATLMediaContentProvider.c',
    'src/api-impl-jni/content/android_content_ClipboardManager.c',
    'src/api-impl-jni/content/android_content_ContentResolver.c',
    'src/api-impl-jni/content/android_content_Context.c',
    'src/api-impl-jni/egl/com_google_android_gles_jni_EGLImpl.c',
    'src/api-impl-jni/gtk_util.c',
    'src/api-impl-jni/graphics/NinePatchPaintable.c',
    'src/api-impl-jni/graphics/android_atl_GskCanvas.c',
    'src/api-impl-jni/graphics/android_graphics_Bitmap.c',
    'src/api-impl-jni/graphics/android_graphics_BitmapFactory.c',
    'src/api-impl-jni/graphics/android_graphics_Matrix.c',
    'src/api-impl-jni/graphics/android_graphics_Paint.c',
    'src/api-impl-jni/graphics/android_graphics_Path.c',
    'src/api-impl-jni/graphics/android_graphics_Region.c',
    'src/api-impl-jni/graphics/android_view_RenderNode.c',
    'src/api-impl-jni/graphics/android_graphics_drawable_Drawable.c',
    'src/api-impl-jni/graphics/android_graphics_drawable_DrawableContainer.c',
    'src/api-impl-jni/graphics/android_graphics_drawable_NinePatchDrawable.c',
    'src/api-impl-jni/location/android_location_LocationManager.c',
    'src/api-impl-jni/media/android_media_MediaCodec.c',
    'src/api-impl-jni/media/android_media_MediaPlayer.c',
    'src/api-impl-jni/media/android_media_session_MediaSession.c',
    'src/api-impl-jni/net/android_net_ConnectivityManager.c',
    'src/api-impl-jni/os/android_atl_GVariantBuilderParcel.c',
    'src/api-impl-jni/os/android_atl_GVariantIterParcel.c',
    'src/api-impl-jni/security/android_security_keystore_AndroidKeyStore.c',
    'src/api-impl-jni/sensors/android_hardware_SensorManager.c',
    'src/api-impl-jni/text/android_text_Layout.c',
    'src/api-impl-jni/views/android_view_inputmethod_InputMethodManager.c',
    'src/api-impl-jni/views/android_view_View.c',
    'src/api-impl-jni/views/android_view_ViewGroup.c',
    'src/api-impl-jni/views/android_view_ViewTreeObserver.c',
    'src/api-impl-jni/views/android_view_WindowManagerImpl.c',
    'src/api-impl-jni/widgets/WrapperWidget.c',
    'src/api-impl-jni/widgets/android_view_SurfaceView.c',
    'src/api-impl-jni/widgets/android_webkit_WebView.c',
    'src/api-impl-jni/widgets/android_widget_Button.c',
    'src/api-impl-jni/widgets/android_widget_CheckBox.c',
    'src/api-impl-jni/widgets/android_widget_CompoundButton.c',
    'src/api-impl-jni/widgets/android_widget_DatePicker.c',
    'src/api-impl-jni/widgets/android_widget_EditText.c',
    'src/api-impl-jni/widgets/android_widget_ImageButton.c',
    'src/api-impl-jni/widgets/android_widget_ImageView.c',
    'src/api-impl-jni/widgets/android_widget_PopupMenu.c',
    'src/api-impl-jni/widgets/android_widget_PopupWindow.c',
    'src/api-impl-jni/widgets/android_widget_Progressbar.c',
    'src/api-impl-jni/widgets/android_widget_RadioButton.c',
    'src/api-impl-jni/widgets/android_widget_ScrollView.c',
    'src/api-impl-jni/widgets/android_widget_Spinner.c',
    'src/api-impl-jni/widgets/android_widget_SeekBar.c',
    'src/api-impl-jni/widgets/android_widget_TextView.c',
    'src/api-impl-jni/widgets/android_widget_TimePicker.c',
    mpris,
    portal_openuri,
    portal_email,
    unifiedpush_distributor,
    unifiedpush_connector,
    wl_proto_headers,
    wl_proto_sources,
    extra_jni_srcs,
    marshal_files,
  ]
  legacy_deps = [
  	extra_deps,
  	dependency('gtk4', version: '>=4.14'), dependency('gl'), dependency('egl'), dependency('wayland-client'),
  	dependency('libportal'), dependency('libavcodec', version: '>=59'), dependency('libdrm'),
  	dependency('gudev-1.0'), dependency('libswscale'), dependency('webkitgtk-6.0'), dependency('libsecret-1'),
  	wayland_protos_dep,
  ]
  legacy_link_with = [libandroid_so]
  legacy_link_args = ['-lasound']
endif

if loop_backend == 'glib'
  backend_libs += static_library('atl_loop_glib', 'src/backends/loop/glib/loop.c',
                                 dependencies: dependency('glib-2.0'), link_with: libandroid_so)
elif loop_backend == 'cfrunloop'
  backend_libs += static_library('atl_loop_cfrunloop', 'src/backends/loop/cfrunloop/loop.c')
else
  backend_libs += static_library('atl_loop_poll', 'src/backends/loop/poll/loop.c',
                                 dependencies: dependency('threads'))
endif

if host_backend == 'gtk'
  backend_libs += static_library('atl_host_gtk', 'src/backends/host/gtk/host.c')
elif host_backend == 'cli'
  backend_libs += static_library('atl_host_cli', 'src/backends/host/cli/host.c')
endif

if ui_backend == 'headless'
  backend_libs += static_library('atl_ui_headless', ['src/backends/ui/records/ui.c', 'src/backends/ui/headless/dialog.c'])
elif ui_backend == 'uikit'
  # views are headless records until uikit draws them
  backend_libs += static_library('atl_ui_uikit', ['src/backends/ui/records/ui.c', 'src/backends/ui/uikit/dialog.m'])
else
  backend_libs += static_library('atl_ui_gtk', [
                                   'src/api-impl-jni/views/AndroidLayout.c',
                                   'src/backends/ui/gtk/dialog.c',
                                   'src/backends/ui/gtk/display.c',
                                   'src/backends/ui/gtk/text.c',
                                   'src/backends/ui/gtk/view.c',
                                   'src/backends/ui/gtk/window.c',
                                 ],
                                 dependencies: [dependency('gtk4'), dependency('libportal'), dependency('jni')])
endif

if gfx_backend == 'record'
  backend_libs += static_library('atl_gfx_record', 'src/backends/gfx/record/paint.c')
else
  backend_libs += static_library('atl_gfx_gsk', 'src/backends/gfx/gsk/paint.c',
                                 dependencies: [dependency('gtk4'), dependency('jni')])
endif

if host_backend == 'embedded'
  # the app links it statically: no dlopen, natives are found in the app image
  static_library('translation_layer_main', 'src/backends/host/embedded/embedded.c',
                 link_whole: [atl_shared] + backend_libs,
                 dependencies: [jni_dep, art_incdir_dep],
                 install: true)
  install_headers('src/backends/host/embedded/atl_embedded.h', subdir: 'atl')
else
  if not desktop
    # a missing slot function or stray reference fails here instead of at ART's RTLD_NOW load;
    # the desktop build resolves symbols from the gtk executable at run time
    legacy_deps += [cc.find_library('log', dirs: libart_dep.get_variable(pkgconfig: 'libdir') / 'art'), cc.find_library('m')]
    legacy_link_args += '-Wl,--no-undefined'
  endif

  libtranslationlayer_so = shared_library('translation_layer_main', legacy_srcs,
  	link_whole: [atl_shared] + backend_libs,
  	install: true,
  	install_dir: get_option('libdir') / 'java/dex/android_translation_layer/natives',
  	install_rpath: '$ORIGIN/:' + get_option('prefix') / get_option('libdir') / 'art',
  	dependencies: [jni_dep, sqlite3_dep, libart_dep] + legacy_deps,
  	link_with: legacy_link_with,
  	link_args: legacy_link_args,
  	c_args: ['-D_LARGEFILE64_SOURCE'] + legacy_c_args)
endif

if host_backend == 'gtk'
  # atl.gresources.xml
  resources = gnome.compile_resources('com.gitlab.android_translation_layer.android_translation_layer', files('src/main-executable/atl.gresource.xml'))

  executable('android-translation-layer', [
                                          	'src/main-executable/main.c',
                                          	'src/main-executable/actions.c',
                                          	'src/main-executable/back_button.c',
                                          	'src/main-executable/bionic_compat.c',
                                          	'src/main-executable/libc_bio_path_overrides.c',
                                          	'src/shared/host_linux.c',
                                          	resources
                                          ],
                                          install: true,
                                          dependencies: [
                                          	dependency('gtk4'), jni_dep, declare_dependency(link_with: libtranslationlayer_so), libart_dep, dependency('dl'), cc.find_library('dl_bio'), cc.find_library('c_bio'), dependency('libportal'), dependency('fontconfig'),
                                          ],
                                          link_args: [
                                          	'-rdynamic'
                                          ],
                                          install_rpath: get_option('prefix') / get_option('libdir') / 'art' + ':' +
                                                         get_option('prefix') / get_option('libdir') / 'java/dex/android_translation_layer/natives',
                                          c_args: [
  						'-DINSTALL_DATADIR="'+join_paths(get_option('prefix'), get_option('datadir'))+'"'
                                          ])
endif

if host_backend == 'cli'
  executable('android-translation-layer', [
               'src/backends/host/cli/main.c',
               'src/main-executable/bionic_compat.c',
               'src/shared/host_linux.c',
             ],
             dependencies: [jni_dep, declare_dependency(link_with: libtranslationlayer_so), libart_dep, cc.find_library('dl_bio')],
             install: true,
             install_rpath: get_option('prefix') / get_option('libdir') / 'art' + ':' +
                            get_option('prefix') / get_option('libdir') / 'java/dex/android_translation_layer/natives')
endif

# Java runs on the build machine; a cross build takes the jars and JNI headers from a native build
if not meson.is_cross_build()
  add_languages('java')

  bootclasspath_dir = libart_dep.get_variable(pkgconfig: 'libdir') / 'java'
  bootclasspath = bootclasspath_dir / 'core-all_classes.jar' + ':' + bootclasspath_dir / 'core-junit_classes.jar' + ':' + bootclasspath_dir / 'junit-runner_classes.jar'

  # dex: d8 plus a manifest (see build-aux/d8-jar.sh)
  d8_jar = find_program('build-aux/d8-jar.sh')

  # hax.jar
  subdir('src/api-impl')

  # api-impl.jar
  custom_target('api-impl.jar', build_by_default: true, input: [hax_jar], output: ['api-impl.jar'],
                install: true,
                install_dir : get_option('libdir') / 'java/dex/android_translation_layer',
                command: [d8_jar, join_paths(builddir_base, 'api-impl.jar'), hax_jar.full_path()])

  # gstub.jar
  subdir('src/gstub')

  custom_target('gstub.jar', build_by_default: true, input: [gstub_jar], output: ['gstub.jar'],
                install: true,
                install_dir : get_option('libdir') / 'java/dex/android_translation_layer',
                command: [d8_jar, join_paths(builddir_base, 'gstub.jar'), gstub_jar.full_path()])

  # ghax.jar
  subdir('src/ghax')

  custom_target('ghax.jar', build_by_default: true, input: [ghax_jar], output: ['ghax.jar'],
                install: true,
                install_dir : get_option('libdir') / 'java/dex/android_translation_layer',
                command: [d8_jar, join_paths(builddir_base, 'ghax.jar'), ghax_jar.full_path()])

  # test-runner.jar
  subdir('src/test-runner')

  custom_target('test_runner.jar', build_by_default: false, input: [test_runner_jar], output: ['test_runner.jar'],
                install: true,
                install_dir : get_option('libdir') / 'java/dex/android_translation_layer',
                command: [d8_jar, join_paths(builddir_base, 'test_runner.jar'), test_runner_jar.full_path()])

  #framework-res.apk
  subdir('res/framework-res')

  install_data([
               	'res/fonts.xml'
               ],
               install_dir: join_paths(get_option('prefix'), get_option('datadir')) + '/atl/system/etc')
endif
