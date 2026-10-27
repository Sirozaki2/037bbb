## ปลั๊กอินสำหรับ Cloudstream

เพิ่ม repository ด้วย `https://raw.githubusercontent.com/popo0606/037/master/repo.json`
แล้วติดตั้งปลั๊กอินที่ต้องการจากรายการส่วนขยาย:

| ปลั๊กอิน | ชื่อแหล่งในแอป | รองรับ |
| --- | --- | --- |
| ExampleProvider | 037-HDD | หนังและซีรีส์ |
| TwentyFiveHD | 25-HD | หนัง ค้นหา ซีซันและตอนที่เว็บมีลิงก์แล้ว |
| MovieDoFree | Moviedofree | หนังและค้นหา |
| AHDZero | AHDZero | อนิเมะ ซีรีส์และเดอะมูฟวี่ พากย์ไทย/ซับไทย |
| TwentyFourHD | 24HD | หนังและซีรีส์ แยกตอนตามลิงก์ที่มีจริง |

แต่ละเว็บติดตั้งแยกกันได้ สำหรับ 25-HD วิดีโอบางรายการรวมหลายเสียงและคำบรรยายไว้ในลิงก์เดียว
ให้เลือกแทร็กเสียงและคำบรรยายในเครื่องเล่น ความพร้อมของวิดีโอขึ้นอยู่กับเว็บต้นทาง

เวอร์ชัน 037-HDD v7, 25-HD v2 และ Moviedofree v2 เพิ่มหมวดหมู่ตามเมนูของแต่ละเว็บ
รวมประเภท ประเทศ ผู้ให้บริการ และปีที่เว็บแสดง เลือกหมวดที่ต้องการในตัวกรองหน้าแรกได้
บางหมวดอาจว่างตามเว็บต้นทาง

25-HD v2 อ่านเส้นทาง CDN จาก HLS content steering เพื่อโหลดชิ้นส่วนวิดีโอ เสียง และซับ
ยังคงใช้ master playlist เดิมเพื่อให้เลือกแทร็กเสียงและคำบรรยายในเครื่องเล่นได้
Moviedofree v2 เพิ่ม Referer สำหรับรูป รองรับรูป lazy-load และแก้การเปลี่ยนหน้าของหมวดปี

AHDZero v1 และ 24HD v1 อ่านหมวดหมู่และตอนจาก HTML ตัวอย่างใน `samples/`
AHDZero มีหมวดซับไทย พากย์ไทย เดอะมูฟวี่ และ genre; 24HD มีประเภทและชุดหนังตาม tag
24HD อ่านรูปปกจาก CSS รวมรูปที่เว็บซ่อนด้วย lazy-load และใช้ลิงก์เปลี่ยนหน้าของรายการหลัก
ทั้งสองใช้ CloudflareKiller ของ Cloudstream เมื่อเว็บส่ง challenge แต่ยังต้องทดสอบบนเครื่องจริง
การตรวจใน sandbox ยืนยันตัวอ่าน HTML และไฟล์สตรีมตัวอย่าง ไม่ยืนยันว่า Cloudflare ผ่านทุกเครื่อง

FWIP และ Movie2Free ยังไม่รวมในแพ็กเกจ: FWIP ส่งต่อไปตัวเล่นที่ sandbox เข้าไม่ได้
ส่วน Movie2Free ตัวอย่างอ้าง `movie_files/saved_resource.html` ซึ่งยังไม่ได้แนบ
ต้องมี HTML/URL ของตัวเล่นเพิ่มก่อนตรวจและเผยแพร่
`movie2freehd.com` เปิดรายการและ API ตัวเล่นได้ แต่ตัวเล่นตัวอย่างส่งต่อไปหน้า `blocked`
บางเรื่องไม่มีตัวเลือกตัวเล่น จึงยังไม่เพิ่มเป็นปลั๊กอินที่อ้างว่าดูได้

ทดสอบปลั๊กอินใหม่ด้วย `./gradlew :AHDZero:testDebugUnitTest :TwentyFourHD:testDebugUnitTest`
(ใช้ไฟล์ HTML ใน `samples/` โดยไม่อ่าน `temp`)
และสร้างแพ็กเกจด้วย `./gradlew make makePluginsJson`

---

**⚠️ This is currently under development, dont use it yet if you're not comfortable with constantly merging new changes**

# `Cloudstream3 Plugin Repo Template`

Template for a [Cloudstream3](https://github.com/recloudstream) plugin repo

**⚠️ Make sure you check "Include all branches" when using this template**

 
## Getting started with writing your first plugin

This template includes 1 example plugin.

1. Open the root build.gradle.kts, read the comments and replace all the placeholders
2. Familiarize yourself with the project structure. Most files are commented
3. Build or deploy your first plugin using:
   - Windows: `.\gradlew.bat ExampleProvider:make` or `.\gradlew.bat ExampleProvider:deployWithAdb`
   - Linux & Mac: `./gradlew ExampleProvider:make` or `./gradlew ExampleProvider:deployWithAdb`


## Granting All Files Access on Newer Android Devices

For local plugin testing, you need to grant the app "All Files Access" on newer Android devices (Android 11 and above). Here’s how to do it:

### Using ADB

* `adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow`
* Replace `PACKAGE_NAME` with the name of the package for the Cloudstream3 version you are using:
   - debug: `com.lagradost.cloudstream3.prerelease.debug`
   - prerelease: `com.lagradost.cloudstream3.prerelease`
   - stable: `com.lagradost.cloudstream3`

### Manually

1. **Open Settings**: Go to your device’s Settings menu.

2. **Navigate to Special Access**:
   - Tap on "Apps & notifications" or "Apps".
   - Select "Special app access" or "Special access".

3. **Select All Files Access**:
   - Tap on "All files access".
   - It may be under the three vertical dots menu towards the top of the screen.

4. **Grant Access to the App**: Find the app in the list and tap on it to toggle it, if it is not already enabled.

6. **Restart the App**: Close and reopen the app to apply the changes.


## License

Everything in this repo is released into the public domain. You may use it however you want with no conditions whatsoever


## Attribution

This template as well as the gradle plugin and the whole plugin system is **heavily** based on [Aliucord](https://github.com/Aliucord).
*Go use it, it's a great mobile discord client mod!*
