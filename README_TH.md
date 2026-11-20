# LUMORIE Pic a Box — Android project (prototype)

โปรเจกต์ Android Studio สำหรับนำหน้าเว็บต้นแบบมาเปิดแบบออฟไลน์ในแอป และมีโครงสร้างพิมพ์ ESC/POS ผ่าน Bluetooth Classic (SPP) และ USB Bulk OUT

## เปิดโปรเจกต์
1. ติดตั้ง Android Studio รุ่นปัจจุบัน
2. เปิดโฟลเดอร์ `LUMORIE_Android_Project`
3. ให้ Gradle Sync ดาวน์โหลด Android Gradle Plugin/Kotlin plugin
4. ใช้ Android SDK Platform 36 และ JDK 17
5. ต่อแท็บเล็ต Android 16 แล้วกด Run เพื่อทดสอบ
6. สร้าง APK: Build > Build Bundle(s) / APK(s) > Build APK(s)

## ข้อจำกัดที่ต้องทราบก่อนใช้งานจริง
- นี่คือ source project ไม่ใช่ APK ที่ build และทดสอบแล้ว
- จากข้อมูลผู้ใช้ XP-80T พิมพ์ผ่าน Bluetooth ด้วยแอปอื่นได้ และ Android แสดงอุปกรณ์ชื่อ Printer001 ในรายการที่บันทึกไว้ จึงตั้ง Bluetooth Classic SPP เป็นแนวทางทดสอบแรก
- ต้องจับคู่ Bluetooth Classic ใน Settings ของ Android ก่อน แอปจะเลือกอุปกรณ์ที่จับคู่ไว้และลอง SPP UUID มาตรฐาน
- การพิมพ์จริงยังต้องทดสอบกับ Printer001 เพราะแอปอื่นอาจใช้ BLE หรือโปรโตคอล/บริการเฉพาะ แม้ผู้ใช้จะเรียกรวมว่า Bluetooth
- BLE ของ Xprinter แต่ละรุ่นใช้ GATT service/characteristic และรูปแบบคำสั่งต่างกัน ต้องทราบรุ่นและเอกสารโปรโตคอลเพื่อทำให้รองรับจริง
- USB ต้องให้ Android แสดงหน้าต่างอนุญาต USB และเครื่องพิมพ์ต้องรับ ESC/POS ผ่าน USB bulk endpoint
- โปรเจกต์นี้เป็นฐานเริ่มต้นสำหรับทดสอบ ไม่ควรใช้กับงานขายจริงจนกว่าจะทดสอบรุ่นเครื่องพิมพ์ กระดาษ และการตัดกระดาษ
- หน้าเว็บเดิมมีปุ่มพิมพ์ผ่าน browser; การเชื่อมปุ่มเข้ากับ native bridge (`Lumorie.printBluetooth(...)` / `Lumorie.printUsb(...)`) และเพิ่ม UI เลือกช่องทางพิมพ์ เป็นงานเชื่อมต่อขั้นถัดไป

## สิ่งที่ต้องส่งเพื่อปรับให้ตรงเครื่อง
ถ่ายรูปสติกเกอร์ใต้/หลังเครื่อง Xprinter หรือส่งชื่อรุ่นและรหัสรุ่น รวมถึงแจ้งว่า Bluetooth แสดงเป็น paired device ใน Android หรือไม่


## ข้อมูลจากภาพ AM Booth ที่ผู้ใช้ส่ง
- หน้าจอ AM Booth แสดงสถานะ `Connected to Printer001` และรายการ `Printer001` เป็น Connected
- หน้าจอเดียวกันแสดงส่วน USB ว่าไม่พบเครื่องพิมพ์ USB ในขณะถ่ายภาพ
- โค้ดแอปจึงใช้ชื่อ `Printer001` เป็นชื่อที่ควรตรวจสอบ/เลือกก่อน แต่ยังคงให้ผู้ใช้เลือกจากรายการอุปกรณ์ที่จับคู่ไว้
- ภาพหน้าจอไม่แสดง Bluetooth MAC address ครบ และไม่บอกว่า AM Booth ใช้ BLE หรือ Bluetooth Classic SPP ภายใน จึงต้องทดสอบ SPP ในแอป LUMORIE ก่อนสรุปความเข้ากันได้


## วิธีให้ได้ไฟล์ APK โดยใช้ Windows (ไม่ต้องเขียนโค้ด)
### วิธี A: GitHub Actions (ไม่ต้องติดตั้ง Android SDK ในเครื่อง)
1. แตกไฟล์ ZIP
2. สร้าง repository ใหม่บน GitHub และอัปโหลดไฟล์/โฟลเดอร์ทั้งหมดในโปรเจกต์
3. เปิดแท็บ Actions และอนุญาตให้รัน workflow หาก GitHub ถาม
4. เลือก `Build LUMORIE APK` แล้วกด `Run workflow`
5. เมื่อสำเร็จ เปิดรายการ workflow run แล้วดาวน์โหลด Artifact ชื่อ `LUMORIE-Pic-a-Box-debug`
6. แตก artifact จะได้ `app-debug.apk` นำไปติดตั้งบนแท็บเล็ต Android 16

### วิธี B: Build บน Windows
1. ติดตั้ง Android Studio และ Android SDK Platform 36
2. ติดตั้ง JDK 17 และ Gradle 8.13 ให้คำสั่ง `gradle` ใช้งานได้ใน Command Prompt
3. เปิดโฟลเดอร์โปรเจกต์ แล้วดับเบิลคลิก `BUILD_APK_WINDOWS.bat`
4. APK จะอยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

## ข้อจำกัดการทดสอบ
ไฟล์ APK ยังไม่ได้ถูก build หรือทดสอบจากสภาพแวดล้อมนี้ เนื่องจากไม่มี Android SDK/Gradle ติดตั้งอยู่ การ build ผ่าน workflow จะช่วยสร้าง APK แต่ไม่ได้ยืนยันว่าการพิมพ์กับ XP-80T ใช้งานได้จริง ต้องทดสอบบนแท็บเล็ตและเครื่องพิมพ์จริงก่อน
