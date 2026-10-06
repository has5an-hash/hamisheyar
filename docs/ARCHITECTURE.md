# معماری همیشه‌یار 0.4.0

## اصل اصلی

همیشه‌یار دیگر LLM یا موتور گفتار آفلاین داخل APK ندارد. مغز هوش مصنوعی، اپ رسمی ChatGPT و حساب خود کاربر است.

## UI

Jetpack Compose + Material 3 برای خانه، چت، ویس، صندوق و تنظیمات.

## ChatGPT Bridge

ChatGptBridge مسئول:

- تشخیص نصب بودن اپ رسمی ChatGPT
- باز کردن ChatGPT یا صفحه نصب
- تحویل متن با ACTION_SEND
- تحویل فایل/عکس/ویدیو با EXTRA_STREAM
- دادن read permission موقت برای URI
- fallback به clipboard + باز کردن ChatGPT در صورت پشتیبانی نکردن Share مستقیم

این لایه هیچ API Key، رمز عبور یا Session کاربر را ذخیره نمی‌کند.

## Share Flow

ShareReceiverActivity محتوای Share شده را:

1. در صندوق محلی ثبت می‌کند.
2. اگر Live Share روشن باشد، متن و URI را به ChatGPT تحویل می‌دهد.
3. اگر خاموش باشد، برای بعد در صف نگه می‌دارد.

## Notification Companion

NotificationListenerService فقط اعلان برنامه‌هایی را که کاربر فعال کرده دریافت می‌کند. متن قابل مشاهده اعلان در SQLite ذخیره می‌شود. RemoteInput فقط وقتی استفاده می‌شود که خود پیام‌رسان Reply را ارائه کرده باشد.

## Floating Assistant

Foreground Service یک Overlay قابل جابه‌جایی نمایش می‌دهد. فرمان‌های سیستمی مثل «کیه؟» و «چی گفته؟» محلی پاسخ داده می‌شوند؛ درخواست‌های آزاد به ChatGPT تحویل داده می‌شوند.

## Voice

تب ویس، اپ رسمی ChatGPT را باز می‌کند تا کاربر از Voice همان حساب خودش استفاده کند. همیشه‌یار موتور گفتار مستقل یا مدل صوتی دانلود نمی‌کند.

## اصل امنیتی

- بدون Accessibility Automation برای دور زدن محدودیت برنامه‌ها
- بدون READ_SMS و SEND_SMS
- بدون ذخیره credential یا token مربوط به ChatGPT
- دسترسی‌های حساس فقط با رضایت کاربر
