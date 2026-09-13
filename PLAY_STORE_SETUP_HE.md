# Google Play – נצור לשונך

הפרויקט מכין שני קבצים:

1. Natzor-Lashon-Release.apk
   מיועד להתקנה ישירה / חברות טלפונים כשרים.
   כולל מנגנון בדיקת עדכונים מול GitHub Releases.

2. Natzor-Lashon-Play.aab
   מיועד ל-Google Play.
   אינו מבקש REQUEST_INSTALL_PACKAGES.
   העדכונים למשתמשי Google Play מתבצעים דרך Google Play.

## עדכון גרסה בעתיד
משנים רק את הקובץ version.properties:
VERSION_CODE – חייב לעלות בכל גרסה.
VERSION_NAME – לדוגמה 1.2.0.

לאחר מכן מריצים:
Actions > Build Release APK and Play AAB > Run workflow

## חשוב לגבי החתימה
אין להחליף את מפתח החתימה הקבוע.
בעת יצירת האפליקציה ב-Google Play יש להקפיד על Play App Signing ועל בחירת אסטרטגיית מפתחות שלא תיצור התנגשות עם ההפצה הישירה.
