# معماری همیشه‌یار

## لایه‌ها

### UI
Jetpack Compose برای خانه، چت، صندوق و تنظیمات.

### Local AI
فایل GGUF از Storage Access Framework وارد فضای برنامه می‌شود و از طریق llama.cpp روی دستگاه اجرا می‌شود.

### Notification Companion
NotificationListenerService فقط اعلان برنامه‌هایی را که کاربر در تنظیمات فعال کرده دریافت می‌کند. متن اعلان در SQLite ذخیره می‌شود و RemoteInput برای پاسخ سریع، در صورت ارائه شدن توسط پیام‌رسان، استفاده می‌شود.

### Floating Assistant
Foreground Service یک Overlay قابل جابه‌جایی نمایش می‌دهد. کاربر می‌تواند از همان پنجره فرمان متنی یا صوتی بدهد.

### Voice
SpeechRecognizer با اولویت On-device/Offline استفاده می‌شود. TextToSpeech پاسخ‌ها را می‌خواند. VoiceNoteRecorder برای ضبط AAC/M4A استفاده می‌شود.

### Share Queue
ShareReceiverActivity متن و فایل‌های ورودی را دریافت می‌کند. در حالت Live برنامه باز می‌شود و در حالت Queue مورد برای بعد ذخیره می‌شود.

### Web Research
اختیاری است. با OkHttp و Jsoup نتایج عمومی وب گرفته شده و خلاصه نتایج به مدل محلی داده می‌شود.

## اصل امنیتی

قابلیت‌های حساس Opt-in هستند. از دسترسی مستقیم SMS/Call Log و Accessibility Automation برای دور زدن محدودیت پیام‌رسان‌ها استفاده نمی‌شود.
