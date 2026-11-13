GESTURE AI — पहला APK UI प्रोटोटाइप

इसमें शामिल:
- Hand, Voice, Face, Radar और Settings स्क्रीन
- 100 तक Hand/Voice/Face gesture स्लॉट जोड़ने का UI (डेमो)
- कैमरा preview बटन और Master OFF
- 30 color swatches, 30 gradient swatches, 50 text-style विकल्प
- Android APK बनाने के लिए GitHub Actions workflow

जरूरी सीमा:
यह अभी UI prototype है, वास्तविक gesture recognition engine नहीं।
दूसरे ऐप खोलना/उनके अंदर actions करना, unlimited installed apps चुनना, आंख/हाथ की
वास्तविक tracking, और स्क्रीन बंद/दूसरे ऐप में background में लगातार काम करना अभी
implemented नहीं है। इसके लिए native Android code, on-device ML model, app launcher
integration और AccessibilityService/Foreground Service permissions का अलग चरण चाहिए।
यह APK बन जाने पर भी इन advanced सुविधाओं का दावा नहीं करता।

GitHub mobile steps:
1. ZIP को extract करें और सारी files/folders repository की root में upload करें।
2. सुनिश्चित करें workflow path ठीक यह हो: .github/workflows/android.yml
3. Actions tab > Build Gesture AI APK > Run workflow.
4. सफल run में Artifacts से Gesture-AI-debug-apk डाउनलोड करें।
