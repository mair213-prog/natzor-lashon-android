# נצור לשונך — בניית APK דרך GitHub

החבילה הזו מוכנה להעלאה לריפו חדש ב-GitHub.

## העלאה
1. חלץ את קובץ ה-ZIP במחשב.
2. בתוך הריפו החדש ב-GitHub בחר:
   Add file > Upload files
3. העלה את כל התוכן שבתיקייה שחולצה, כולל:
   - app
   - gradle
   - .github
   - build.gradle.kts
   - settings.gradle.kts
   - gradle.properties
   - .gitignore
   - קבצי ההסבר
4. לחץ Commit changes.

## בניית APK
לאחר ההעלאה:
1. פתח את לשונית Actions.
2. בחר Build Natzor Lashon APK.
3. לחץ Run workflow.
4. המתן לסימון ירוק.
5. פתח את הריצה שהסתיימה.
6. בתחתית, באזור Artifacts, הורד:
   Natzor-Lashon-APK
7. בתוך ההורדה נמצא:
   Natzor-Lashon.apk

אין צורך להתקין Android Studio במחשב.

## מה האפליקציה עושה
האפליקציה נפתחת ישירות למערכת "נצור לשונך" ומתחברת לאותו שרת:
https://natzor-lashon.onrender.com

Package:
il.co.natzorlashon.app

Version:
1.0

Target SDK:
36
