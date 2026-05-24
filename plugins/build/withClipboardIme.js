"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_plugins_1 = require("expo/config-plugins");

const IME_SERVICE_CLASS = 'expo.modules.clipboardime.SyncClipboardImeService';

const withClipboardIme = (config) => {
    return (0, config_plugins_1.withAndroidManifest)(config, (modConfig) => {
        const androidManifest = modConfig.modResults;
        const manifest = androidManifest.manifest || {};

        let application = Array.isArray(manifest.application)
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
            (s) => s?.$?.['android:name'] === IME_SERVICE_CLASS
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

exports.default = (0, config_plugins_1.createRunOncePlugin)(
    withClipboardIme,
    'withClipboardIme',
    '1.0.0'
);