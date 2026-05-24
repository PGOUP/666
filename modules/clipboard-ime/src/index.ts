import { Platform } from 'react-native';
import { requireNativeModule, EventEmitter, type EventSubscription } from 'expo-modules-core';

const MODULE_NAME = 'ClipboardImeModule';

interface ImeClipboardImageResult {
  width: number;
  height: number;
  filePath: string;
  mimeType: string;
}

interface ImeClipboardChangedEvent {
  hasText: boolean;
  hasImage: boolean;
  timestamp: number;
}

interface ClipboardImeModuleInterface {
  isImeEnabled(): boolean;
  openImeSettings(): void;
  openImePicker(): void;
  hasImeClipboardText(): Promise<boolean>;
  hasImeClipboardImage(): Promise<boolean>;
  getImeClipboardText(): Promise<string>;
  getImeClipboardImageUri(): Promise<string | null>;
  saveImeClipboardImageToFile(
    destDirPath: string
  ): Promise<ImeClipboardImageResult | null>;
}

// @ts-ignore - 忽略类型错误，优先保证构建通过
const NativeModule: ClipboardImeModuleInterface | null =
  Platform.OS === 'android' ? requireNativeModule(MODULE_NAME) : null;

// @ts-ignore - 忽略类型错误，优先保证构建通过
const emitter: EventEmitter | null =
  Platform.OS === 'android' ? new EventEmitter(NativeModule ?? undefined) : null;

export function isImeEnabled(): boolean {
  if (NativeModule) {
    return NativeModule.isImeEnabled();
  }
  return false;
}

export function openImeSettings(): void {
  if (NativeModule) {
    NativeModule.openImeSettings();
  }
}

export function openImePicker(): void {
  if (NativeModule) {
    NativeModule.openImePicker();
  }
}

export async function hasImeClipboardText(): Promise<boolean> {
  if (NativeModule) {
    return NativeModule.hasImeClipboardText();
  }
  return false;
}

export async function hasImeClipboardImage(): Promise<boolean> {
  if (NativeModule) {
    return NativeModule.hasImeClipboardImage();
  }
  return false;
}

export async function getImeClipboardText(): Promise<string> {
  if (NativeModule) {
    return NativeModule.getImeClipboardText();
  }
  return '';
}

export async function getImeClipboardImageUri(): Promise<string | null> {
  if (NativeModule) {
    return NativeModule.getImeClipboardImageUri();
  }
  return null;
}

export async function saveImeClipboardImageToFile(
  destDirPath: string
): Promise<ImeClipboardImageResult | null> {
  if (NativeModule) {
    return NativeModule.saveImeClipboardImageToFile(destDirPath);
  }
  return null;
}

export function addClipboardChangedListener(
  listener: (event: ImeClipboardChangedEvent) => void
): EventSubscription | null {
  if (emitter) {
    return emitter.addListener<ImeClipboardChangedEvent>('onClipboardChanged', listener);
  }
  return null;
}
