# נצור לשונך — Android v1

זוהי אפליקציית Android עצמאית של מערכת "נצור לשונך".
המשתמש אינו צריך לפתוח דפדפן או להיכנס לאתר כדי להפעיל אותה.

## איך היא עובדת
האפליקציה נפתחת ישירות למסך המערכת ומתחברת לשרת הקיים:
https://natzor-lashon.onrender.com

לכן האתר, ה-PWA ואפליקציית Android עובדים מול אותה מערכת ואותו מסד נתונים.

## בניית APK
פתח את התיקייה ב-Android Studio, המתן ל-Gradle Sync ואז:
Build > Build App Bundles or APKs > Build APKs

לפרסום בחנות:
Build > Generate Signed App Bundle / APK > Android App Bundle

## מכשירים כשרים
האפליקציה אינה דורשת מהמשתמש לפתוח דפדפן, אבל היא כן דורשת:
1. שהמכשיר יאפשר התקנת האפליקציה.
2. שרכיב Android System WebView יהיה קיים/פעיל.
3. שספק הסינון יאפשר לאפליקציה תקשורת אל natzor-lashon.onrender.com.

אם ספק המכשיר חוסם התקנת APK או WebView, צריך להעביר לו את האפליקציה לאישור/הכנסה לחנות המאושרת שלו.

## פרטי גרסה
Package: il.co.natzorlashon.app
Version: 1.0
minSdk: 24
targetSdk: 36
