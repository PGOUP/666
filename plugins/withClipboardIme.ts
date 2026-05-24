import {
  ConfigPlugin,
  withAndroidManifest,
  createRunOncePlugin,
} from 'expo/config-plugins';

const IME_SERVICE_CLASS = 'expo.modules.clipboardime.SyncClipboardImeService';

const withClipboardIme: ConfigPlugin = (config) => {
  return withAndroidManifest(config, (modConfig: any) => {
    const androidManifest = modConfig.modResults;
    const manifest: any = androidManifest.manifest || {};

    let application: any = Array.isArray(manifest.application)
      ? manifest.application[0]
      : manifest.application;
    if (!application) {
      application = {};
      manifest.application = [application];
    }
    if (!Array.isArray(manifest.application)) {
      manifest.application = [application];
    }

    if (!application.service) {
      application.service = [];
    }

    const hasService = application.service.some(
      (s: any) => s?.$?.['android:name'] === IME_SERVICE_CLASS
    );

    if (!hasService) {
      application.service.push({
        $: {
          'android:name': IME_SERVICE_CLASS,
          'android:label': 'SyncClipboard IME',
          'android:permission': 'android.permission.BIND_INPUT_METHOD',
          'android:exported': 'true',
        },
        'intent-filter': [
          {
            action: [
              {
                $: {
                  'android:name': 'android.view.InputMethod',
                },
              },
            ],
          },
        ],
        'meta-data': [
          {
            $: {
              'android:name': 'android.view.im',
              'android:resource': '@xml/ime_method',
            },
          },
        ],
      });
      console.log('✓ Added SyncClipboardImeService to AndroidManifest.xml');
    } else {
      console.log('✓ SyncClipboard IME service already registered');
    }

    return modConfig;
  });
};

export default createRunOncePlugin(
  withClipboardIme,
  'withClipboardIme',
  '1.0.0'
);
