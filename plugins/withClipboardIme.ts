import {
  AndroidConfig,
  ConfigPlugin,
  withAndroidManifest,
  createRunOncePlugin,
} from 'expo/config-plugins';

const IME_SERVICE_CLASS =
  'expo.modules.clipboardime.SyncClipboardImeService';

function addImeService(
  androidManifest: AndroidConfig.Manifest.AndroidManifest,
  packageName: string
): AndroidConfig.Manifest.AndroidManifest {
  const { manifest } = androidManifest;

  if (!manifest.application) {
    manifest.application = [{}];
  }

  const application = manifest.application[0];

  if (!application.service) {
    application.service = [];
  }

  const hasService = application.service.some(
    (s) => s.$?.['android:name'] === IME_SERVICE_CLASS
  );

  if (hasService) {
    console.log('✓ SyncClipboard IME service already registered');
    return androidManifest;
  }

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
  } as NonNullable<(typeof application)['service']>[0]);

  console.log('✓ Added SyncClipboardImeService to AndroidManifest.xml');
  return androidManifest;
}

const withClipboardIme: ConfigPlugin = (config) => {
  const packageName =
    (config as { android?: { package?: string } }).android?.package ??
    'com.jericx.syncclipboardmobile';

  return withAndroidManifest(config, (modConfig) => {
    modConfig.modResults = addImeService(modConfig.modResults, packageName);
    return modConfig;
  });
};

export default createRunOncePlugin(
  withClipboardIme,
  'withClipboardIme',
  '1.0.0'
);
