"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_plugins_1 = require("expo/config-plugins");

const IME_SERVICE_CLASS = 'expo.modules.clipboardime.SyncClipboardImeService';

function addImeService(androidManifest, packageName) {
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
    });
    console.log('✓ Added SyncClipboardImeService to AndroidManifest.xml');
    return androidManifest;
}

const withClipboardIme = (config) => {
    const packageName =
        config.android?.package ?? 'com.jericx.syncclipboardmobile';
    return (0, config_plugins_1.withAndroidManifest)(config, (modConfig) => {
        modConfig.modResults = addImeService(modConfig.modResults, packageName);
        return modConfig;
    });
};

exports.default = (0, config_plugins_1.createRunOncePlugin)(
    withClipboardIme,
    'withClipboardIme',
    '1.0.0'
);
