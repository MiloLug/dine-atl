# Opt-in: regenerating rewrites the committed, clang-formatted R.java/Manifest.java unformatted. To
# change only values, run this aapt command into a scratch dir and copy just the APK.
if get_option('regenerate_framework_res')
  aapt = find_program('aapt')
  framework_res_inputs = []
  aapt_command = [aapt, 'package', '-x', '-f', '--custom-package', 'com.android.internal', '-S', join_paths(dir_base, 'res/framework-res/res'), '-A', join_paths(dir_base, 'res/framework-res/assets'), '-M', join_paths(dir_base, 'res/framework-res/AndroidManifest.xml'), '-J', join_paths(dir_base, 'src/api-impl/com/android/internal'), '-F', '@OUTPUT@']
else  # fallback to prebuilt APK
  framework_res_inputs = files('framework-res.apk')
  aapt_command = ['cp', '@INPUT@', '@OUTPUT@']
endif

  framework_res = custom_target('framework-res.apk',
    input : framework_res_inputs,
    output : 'framework-res.apk',
    command : aapt_command,
    install : true,
    install_dir : get_option('libdir') / 'java/dex/android_translation_layer')
