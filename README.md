## ปลั๊กอินสำหรับ Cloudstream

เพิ่ม repository ด้วย `https://raw.githubusercontent.com/jasdwa22/037bb/master/repo.json`
แล้วติดตั้งปลั๊กอินที่ต้องการจากรายการส่วนขยาย:

| ปลั๊กอิน | ชื่อแหล่งในแอป | รองรับ |
| --- | --- | --- |
| ExampleProvider | 037-HDD | หนังและซีรีส์ |
| TwentyFiveHD | 25-HD | หนัง ค้นหา ซีซันและตอนที่เว็บมีลิงก์แล้ว |
| MovieDoFree | Moviedofree | หนังและค้นหา (ซ่อนเมนูซีรีส์ที่ยังไม่รองรับ) |
| AHDZero | AHDZero | อนิเมะ ซีรีส์และเดอะมูฟวี่ พากย์ไทย/ซับไทย |
| TwentyFourHD | 24HD | หนังและซีรีส์ แยกตอนตามลิงก์ที่มีจริง |
| Movie2FreeHD | Movie2FreeHD | หนัง/ซีรีส์ ค้นหา หมวดหมู่ ตอนและแหล่งเสียงไทย/ซับไทย |
| FWIPTV | FWIP TV | ทดลองสำหรับ Cloudstream รุ่น beta: รายการ/ค้นหา/ซีซัน/ตอน; ตัวเล่นยังไม่ยืนยัน |
| ThaiAnime | Neko-Miku, AnimeRuka, AnimeSeven, Suba-Anime | ทดลองสำหรับ beta: อนิเมะ/หนัง ค้นหา หมวดหมู่ แยกเสียงและตอน; การเล่นยังต้องทดสอบบนอุปกรณ์ |

ปลั๊กอินเดิมติดตั้งแยกกันได้ ส่วน ThaiAnime ติดตั้งครั้งเดียวจะเพิ่มแหล่งอนิเมะทั้งสี่เว็บ สำหรับ 25-HD วิดีโอบางรายการรวมหลายเสียงและคำบรรยายไว้ในลิงก์เดียว
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

## การเพิ่มและแก้ไขรอบนี้

- Movie2FreeHD v1 ใช้หน้า `/movies/` และ `/series/` สำหรับแบ่งหน้า อ่านผลค้นหาแยกจากรายการแนะนำ
  รูปปกรองรับ CSS และ lazy-load; ตอนเรียงตามซีซัน/เลขตอน และรวมลิงก์ซ้ำโดยคงชื่อพากย์ไทย/ซับไทยไว้
  ดึงลิงก์ Meeplayer ใหม่เมื่อกดเล่น รักษา HLS master สำหรับแทร็กเสียง และลอง CDN สำรองเมื่อโหลดชิ้นส่วนวิดีโอผิดพลาด
- FWIP TV v2 เพิ่มจาก HTML ที่แนบใน `samples/fwip-tv.com/` พร้อมเมนูประเภท ปี ประเทศ และเสียง
  ตั้ง `status = 3` (Cloudstream beta เท่านั้น): โฮสต์ `player123up.xyz` ยังถูกบล็อกจาก sandbox
  การเล่นส่งต่อให้ extractor ของ Cloudstream จึงอาจยังไม่มีลิงก์เล่นสำหรับโฮสต์ที่แอปไม่รองรับ
  ไม่ใช้ iframe HTML หรือวิดีโอโฆษณาเป็นลิงก์หนัง และแจ้งสาเหตุเมื่อโฮสต์โหลดไม่ได้หรือไม่มี extractor รองรับ
- ThaiAnime v1 เพิ่ม Neko-Miku, AnimeRuka, AnimeSeven และ Suba-Anime จาก HTML ที่ผู้ใช้บันทึก
  เมนูมาจากเว็บจริง แยก Anime/AnimeMovie, พากย์ไทย/ซับไทยและซีซัน โดยไม่แสดงตอนที่ยังไม่มีลิงก์
  ค้นหา Suba อ่านเฉพาะผลค้นหา ไม่รวมรายการล่าสุดที่เว็บแทรกไว้; รองรับรูป lazy-load และการแบ่งหน้าของแต่ละเว็บ
  AnimeRuka อ่านตัวเลือก REST ใหม่ทุกครั้งที่เล่น; AnimeSeven อ่านตัวเล่นซ้อนและ manifest จาก bootstrap ใหม่
  Suba อ่าน content iframe ของ Kokora ไปยัง anim-esun และเก็บลิงก์วิดีโอจริงตามความละเอียด โดยไม่ใช้ preroll
  โฮสต์ Akuma/Abyss/AnimeMami/OK.ru ส่งต่อให้ extractor ที่มีในแอป ความพร้อมขึ้นกับแอปและเว็บปลายทาง
  ตั้ง status = 3 จนกว่าจะตรวจการเล่นบนอุปกรณ์จริงได้
- 24HD v2 และ AHDZero v2 ใช้ HTTP cache ของแอปสำหรับรายการ/รายละเอียด และโหลดหน้าใหม่ตอนกดเล่น
  ลดการโหลดหน้าแรกซ้ำทางเครือข่ายของ 24HD; รองรับปุ่ม load-more ที่มี `data-next-page` แบบ relative
  AHDZero เรียงตอนตามเลข และ 24HD อ่านรูป lazy-load เมื่อไม่มี CSS รูปปก
- 25-HD v3 แก้ชื่อเมนูปีให้ตรง URL, รองรับรูป lazy-load และไม่ทิ้ง master playlist เมื่อโหลด steering ไม่สำเร็จ
  ตัวเล่นสำรองที่ล้มเหลวไม่ทำให้ลิงก์ที่เจอแล้วหายไป
- 037-HDD v8 เก็บ query เมื่อเปลี่ยนหน้า และข้าม extractor สำรองที่ผิดพลาดเพื่อทำงานกับแหล่งถัดไป
- Moviedofree v3 ซ่อนเมนูซีรีส์ที่ไม่ตรงกับความสามารถของ provider (ยังรองรับหนังเท่านั้น)
  ตรวจหน้าหมวดซีรีส์ฝรั่งแล้วเว็บส่ง “ไม่พบข้อมูล”

### สถานะการตรวจเว็บ

Movie2FreeHD ตรวจหน้าแรก/หนัง/ซีรีส์/ค้นหา/หน้าที่สองและ API ตัวเล่นจริงได้
ตรวจ Meeplayer ของซีรีส์ตัวอย่างถึง HLS master, media playlist และข้อมูลชิ้นส่วนวิดีโอแบบจำกัดขนาดแล้ว
ไม่ได้ยืนยันว่าทุกเรื่องหรือทุกแทร็กเสียงเล่นได้ และยังไม่ได้ทดสอบแอปบนอุปกรณ์ Android
037-HDD, 25-HD และ Moviedofree ตรวจตัวอ่านกับ HTML หน้าแรกที่ดึงใหม่ได้
24HD, AHDZero และ FWIP ถูก Cloudflare บล็อกใน sandbox จึงใช้ HTML ตัวอย่างและ unit tests ตรวจโครงสร้าง

Nekomiku (`nekomiku.net`), AnimeRuka (`animeruka.com`), AnimeSeven (`www.anime-seven.com`)
และ Suba-Anime (`suba-anime.com`) เพิ่มในแพ็กเกจ ThaiAnime แล้ว โดยใช้ samples ที่ผู้ใช้เพิ่มบน master
สามเว็บแรกและ FWIP/ตัวเล่น player123up ยังถูก Cloudflare บล็อกทั้ง HTTP และ Chromium
Suba เปิดหน้าเว็บและตัวเล่นใน Chromium ได้ ตรวจ content source ที่ anim-esun และดึงชิ้นส่วนวิดีโอ
1,024 ไบต์ผ่าน Chromium ได้ HTTP 206 (video/x-m4v) แต่ curl ดึงไฟล์เดียวกันหมดเวลา
จึงยังไม่ได้ยืนยันเส้นทางโหลดวิดีโอของแอปหรือการเล่นจนจบบน Android
ผล tests ยืนยันตัวอ่าน HTML/ตัวเลือกเสียง/เส้นทางตัวเล่น ไม่ยืนยันว่าโฮสต์ทุกตัวเล่นได้
รายละเอียดไฟล์ที่ใช้ได้และตัวอย่างที่ยังขาดอยู่ใน [samples/README.md](samples/README.md)
Movie2FreeHD เป็นคนละเว็บกับ `movie2free.co` ที่มีตัวอย่างเดิมใน repo

ทดสอบทุก provider และสร้างแพ็กเกจ:

```bash
GITHUB_REPOSITORY=jasdwa22/037bb ./gradlew make makePluginsJson testDebugUnitTest
```

ใช้ JDK 17, Android SDK 35 และกำหนด `ANDROID_HOME` ให้ถูกต้อง
Unit tests ใช้ HTML fixtures และ API จำลองในหน่วยทดสอบ จึงไม่ต้องเข้าถึงเว็บต้นทางและไม่อ่านไฟล์ `temp`

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
