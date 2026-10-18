import type { CapacitorConfig } from '@capacitor/cli';

// The app is a window onto your server, so web changes reach phones without a new APK.
// Set APP_URL when you build (see README), for example https://shop.yourdomain.com
const base = (process.env.APP_URL ?? 'https://CHANGE-ME.example.com').replace(/\/$/, '');

const config: CapacitorConfig = {
  appId: 'com.alphamart.delivery',
  appName: 'ALPHAMART Delivery',
  webDir: 'www',
  server: { url: `${base}/app/delivery`, cleartext: base.startsWith('http://'), errorPath: 'offline.html' },
  android: { allowMixedContent: false, backgroundColor: '#12292e' },
  plugins: {
    PushNotifications: { presentationOptions: ['badge', 'sound', 'alert'] },
    // Keep the page clear of the status bar and gesture bar on every Android version (older WebViews get native padding, newer ones get real safe-area values).
    SystemBars: { insetsHandling: 'native' },
  },
};
export default config;
